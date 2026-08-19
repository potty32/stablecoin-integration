package de.atruvia.stablecoin;

import de.atruvia.stablecoin.client.CircleWalletClient;
import de.atruvia.stablecoin.client.dto.CircleTransferResponseDto;
import de.atruvia.stablecoin.client.dto.CircleWalletBalanceDto;
import de.atruvia.stablecoin.dto.request.b2c.MicropaymentRequest;
import de.atruvia.stablecoin.dto.response.CardWalletResponse;
import de.atruvia.stablecoin.dto.response.TransactionResponse;
import de.atruvia.stablecoin.entity.CustomerAccount;
import de.atruvia.stablecoin.entity.StablecoinTransaction;
import de.atruvia.stablecoin.entity.TransactionStatus;
import de.atruvia.stablecoin.exception.IdempotencyConflictException;
import de.atruvia.stablecoin.repository.AuditLogRepository;
import de.atruvia.stablecoin.repository.CustomerAccountRepository;
import de.atruvia.stablecoin.repository.StablecoinTransactionRepository;
import de.atruvia.stablecoin.service.b2c.B2cMicropaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit-Tests für B2cMicropaymentService — kein Spring-Kontext.
 */
class B2cMicropaymentServiceTest {

    @Mock StablecoinTransactionRepository txRepository;
    @Mock CustomerAccountRepository accountRepository;
    @Mock AuditLogRepository auditLogRepository;
    @Mock CircleWalletClient circleWalletClient;

    @InjectMocks B2cMicropaymentService service;

    private static final String IBAN          = "DE27200400600532013001";
    private static final String WALLET        = "0xB2CCustomerWallet";
    private static final String CUSTOMER_ID   = "cust-b2c-001";
    private static final String MERCHANT_ID   = "MERCHANT-42";
    private static final String BIOMETRIC     = "biometric-valid-token-2026";
    private static final String IDEM_KEY      = UUID.randomUUID().toString();

    private CustomerAccount account;
    private CircleTransferResponseDto circleOk;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        account = new CustomerAccount();
        account.setIban(IBAN);
        account.setWalletAddress(WALLET);
        account.setCustomerId(CUSTOMER_ID);
        ReflectionTestUtils.setField(account, "id", UUID.randomUUID());

        circleOk = new CircleTransferResponseDto("circle-micro-001", "COMPLETE", "0xhash", null);

        StablecoinTransaction savedTx = new StablecoinTransaction();
        ReflectionTestUtils.setField(savedTx, "id", UUID.randomUUID());
        savedTx.setStatus(TransactionStatus.SETTLED);

        when(txRepository.findByIdempotencyKey(IDEM_KEY)).thenReturn(Optional.empty());
        when(accountRepository.findByIban(IBAN)).thenReturn(Optional.of(account));
        when(circleWalletClient.initiateTransfer(any())).thenReturn(circleOk);
        when(txRepository.save(any())).thenReturn(savedTx);
        when(auditLogRepository.save(any())).thenReturn(null);
    }

    // ─── TC-MICRO-01: Happy Path ──────────────────────────────────────────────

    @Test
    @DisplayName("TC-MICRO-01: Micropayment unter 10 EUR → SETTLED")
    void pay_validRequest_returnsSettled() {
        MicropaymentRequest request = new MicropaymentRequest(
                IBAN, MERCHANT_ID, new BigDecimal("5.00"), "article-123", BIOMETRIC
        );

        TransactionResponse response = service.pay(IDEM_KEY, request, "user-b2c-001");

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(TransactionStatus.SETTLED);
        verify(circleWalletClient).initiateTransfer(any());
        verify(auditLogRepository).save(any());
    }

    // ─── TC-MICRO-02: Betragslimit ────────────────────────────────────────────

    @Test
    @DisplayName("TC-MICRO-02: Betrag exakt 10 EUR → erlaubt")
    void pay_exactMaxAmount_allowed() {
        MicropaymentRequest request = new MicropaymentRequest(
                IBAN, MERCHANT_ID, BigDecimal.TEN, "article-123", BIOMETRIC
        );

        TransactionResponse response = service.pay(IDEM_KEY, request, "user-001");

        assertThat(response.status()).isEqualTo(TransactionStatus.SETTLED);
    }

    @ParameterizedTest(name = "Betrag={0} EUR → abgelehnt")
    @ValueSource(strings = {"10.01", "15.00", "100.00", "10.001"})
    @DisplayName("TC-MICRO-03: Betrag über 10 EUR → IllegalArgumentException")
    void pay_amountExceedsMax_throwsIllegalArgument(String amount) {
        MicropaymentRequest request = new MicropaymentRequest(
                IBAN, MERCHANT_ID, new BigDecimal(amount), "article-123", BIOMETRIC
        );

        assertThatThrownBy(() -> service.pay(IDEM_KEY, request, "user-001"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds maximum");

        verify(circleWalletClient, never()).initiateTransfer(any());
    }

    // ─── TC-MICRO-04: Biometrie-Validierung ──────────────────────────────────

    @ParameterizedTest(name = "Token={0}")
    @ValueSource(strings = {"", "short", "123456789"})
    @DisplayName("TC-MICRO-04: Biometric-Token zu kurz (< 10 Zeichen) → IllegalArgumentException")
    void pay_shortBiometricToken_throwsIllegalArgument(String token) {
        MicropaymentRequest request = new MicropaymentRequest(
                IBAN, MERCHANT_ID, new BigDecimal("5.00"), "article-123", token
        );

        assertThatThrownBy(() -> service.pay(IDEM_KEY, request, "user-001"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("biometricToken");
    }

    @Test
    @DisplayName("TC-MICRO-05: Null-Biometric-Token → IllegalArgumentException")
    void pay_nullBiometricToken_throwsIllegalArgument() {
        MicropaymentRequest request = new MicropaymentRequest(
                IBAN, MERCHANT_ID, new BigDecimal("5.00"), "article-123", null
        );

        assertThatThrownBy(() -> service.pay(IDEM_KEY, request, "user-001"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ─── TC-MICRO-06: Idempotenz-Konflikt ────────────────────────────────────

    @Test
    @DisplayName("TC-MICRO-06: Doppelter Idempotency-Key → IdempotencyConflictException")
    void pay_duplicateIdempotencyKey_throwsConflict() {
        StablecoinTransaction existingTx = new StablecoinTransaction();
        ReflectionTestUtils.setField(existingTx, "id", UUID.randomUUID());
        when(txRepository.findByIdempotencyKey(IDEM_KEY)).thenReturn(Optional.of(existingTx));

        MicropaymentRequest request = new MicropaymentRequest(
                IBAN, MERCHANT_ID, new BigDecimal("5.00"), "article-123", BIOMETRIC
        );

        assertThatThrownBy(() -> service.pay(IDEM_KEY, request, "user-001"))
                .isInstanceOf(IdempotencyConflictException.class);
    }

    // ─── TC-MICRO-07: Konto nicht gefunden ───────────────────────────────────

    @Test
    @DisplayName("TC-MICRO-07: IBAN nicht gefunden → NoSuchElementException")
    void pay_accountNotFound_throwsNotFound() {
        when(accountRepository.findByIban(IBAN)).thenReturn(Optional.empty());

        MicropaymentRequest request = new MicropaymentRequest(
                IBAN, MERCHANT_ID, new BigDecimal("5.00"), "article-123", BIOMETRIC
        );

        assertThatThrownBy(() -> service.pay(IDEM_KEY, request, "user-001"))
                .isInstanceOf(java.util.NoSuchElementException.class)
                .hasMessageContaining(IBAN);
    }

    // ─── TC-MICRO-08: getCardWallet ──────────────────────────────────────────

    @Test
    @DisplayName("TC-MICRO-08: getCardWallet — USDC und EURC Balances korrekt")
    void getCardWallet_returnsCorrectBalances() {
        when(accountRepository.findByCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(account));

        CircleWalletBalanceDto balance = new CircleWalletBalanceDto(WALLET,
                List.of(
                        new CircleWalletBalanceDto.Balance("USDC", "150.000000"),
                        new CircleWalletBalanceDto.Balance("EURC", "75.500000")
                )
        );
        when(circleWalletClient.getWalletBalance(WALLET)).thenReturn(balance);

        CardWalletResponse response = service.getCardWallet(CUSTOMER_ID);

        assertThat(response.walletAddress()).isEqualTo(WALLET);
        assertThat(response.balanceUsdc()).isEqualTo("150.000000");
        assertThat(response.balanceEurc()).isEqualTo("75.500000");
    }

    @Test
    @DisplayName("TC-MICRO-09: getCardWallet — keine Balances → 0.000000")
    void getCardWallet_noBalances_returnsZero() {
        when(accountRepository.findByCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(account));

        CircleWalletBalanceDto balance = new CircleWalletBalanceDto(WALLET, List.of());
        when(circleWalletClient.getWalletBalance(WALLET)).thenReturn(balance);

        CardWalletResponse response = service.getCardWallet(CUSTOMER_ID);

        assertThat(response.balanceUsdc()).isEqualTo("0.000000");
        assertThat(response.balanceEurc()).isEqualTo("0.000000");
    }

    @Test
    @DisplayName("TC-MICRO-10: getCardWallet — Kunde nicht gefunden → NoSuchElementException")
    void getCardWallet_customerNotFound_throwsNotFound() {
        when(accountRepository.findByCustomerId(CUSTOMER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCardWallet(CUSTOMER_ID))
                .isInstanceOf(java.util.NoSuchElementException.class)
                .hasMessageContaining(CUSTOMER_ID);
    }
}
