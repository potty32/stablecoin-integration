package de.atruvia.stablecoin;

import de.atruvia.stablecoin.client.CircleWalletClient;
import de.atruvia.stablecoin.client.dto.CircleTransferResponseDto;
import de.atruvia.stablecoin.dto.request.b2c.P2pPhoneRequest;
import de.atruvia.stablecoin.dto.request.b2c.RegisterPhoneAliasRequest;
import de.atruvia.stablecoin.dto.response.TransactionResponse;
import de.atruvia.stablecoin.entity.CustomerAccount;
import de.atruvia.stablecoin.entity.PhoneAlias;
import de.atruvia.stablecoin.entity.StablecoinTransaction;
import de.atruvia.stablecoin.entity.TransactionStatus;
import de.atruvia.stablecoin.exception.IdempotencyConflictException;
import de.atruvia.stablecoin.repository.AuditLogRepository;
import de.atruvia.stablecoin.repository.CustomerAccountRepository;
import de.atruvia.stablecoin.repository.PhoneAliasRepository;
import de.atruvia.stablecoin.repository.StablecoinTransactionRepository;
import de.atruvia.stablecoin.service.b2c.B2cP2pService;
import de.atruvia.stablecoin.service.b2c.PhoneHashService;
import de.atruvia.stablecoin.service.revenue.RevenueService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit-Tests für B2cP2pService — kein Spring-Kontext.
 */
class B2cP2pServiceTest {

    @Mock StablecoinTransactionRepository txRepository;
    @Mock CustomerAccountRepository accountRepository;
    @Mock PhoneAliasRepository phoneAliasRepository;
    @Mock AuditLogRepository auditLogRepository;
    @Mock CircleWalletClient circleWalletClient;
    @Mock RevenueService revenueService;
    @Mock PhoneHashService phoneHashService;

    @InjectMocks B2cP2pService service;

    private static final String SENDER_IBAN    = "DE89370400440532013000";
    private static final String SENDER_WALLET  = "0xSenderWallet";
    private static final String RECEIVER_WALLET = "0xReceiverWallet";
    private static final String PHONE          = "+491701234567";
    private static final String PHONE_HASH     = "sha256-phone-hash";
    private static final String IDEM_KEY       = UUID.randomUUID().toString();

    private CustomerAccount senderAccount;
    private PhoneAlias receiverAlias;
    private CircleTransferResponseDto circleOk;
    private RevenueService.RevenueCalculation revenue;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        ReflectionTestUtils.setField(service, "feeB2c", new BigDecimal("0.50"));

        senderAccount = new CustomerAccount();
        senderAccount.setIban(SENDER_IBAN);
        senderAccount.setWalletAddress(SENDER_WALLET);
        ReflectionTestUtils.setField(senderAccount, "id", UUID.randomUUID());

        receiverAlias = new PhoneAlias();
        receiverAlias.setPhoneNumberHash(PHONE_HASH);
        receiverAlias.setWalletAddress(RECEIVER_WALLET);

        circleOk = new CircleTransferResponseDto("circle-p2p-001", "COMPLETE", "0xhash", null);
        revenue   = new RevenueService.RevenueCalculation(
                new BigDecimal("0.0015"), new BigDecimal("0.75"),
                new BigDecimal("0.50"), new BigDecimal("0.008"), new BigDecimal("1.25")
        );

        StablecoinTransaction savedTx = new StablecoinTransaction();
        ReflectionTestUtils.setField(savedTx, "id", UUID.randomUUID());
        savedTx.setStatus(TransactionStatus.SETTLED);

        when(phoneHashService.hash(PHONE)).thenReturn(PHONE_HASH);
        when(txRepository.findByIdempotencyKey(IDEM_KEY)).thenReturn(Optional.empty());
        when(accountRepository.findByIban(SENDER_IBAN)).thenReturn(Optional.of(senderAccount));
        when(phoneAliasRepository.findByPhoneNumberHash(PHONE_HASH)).thenReturn(Optional.of(receiverAlias));
        when(circleWalletClient.initiateTransfer(any())).thenReturn(circleOk);
        when(txRepository.save(any())).thenReturn(savedTx);
        when(auditLogRepository.save(any())).thenReturn(null);
        when(revenueService.calculate(any(), any())).thenReturn(revenue);
    }

    // ─── TC-P2P-01: Telefon-Alias registrieren ───────────────────────────────

    @Test
    @DisplayName("TC-P2P-01: registerPhoneAlias — erfolgreich → PhoneAlias gespeichert")
    void registerPhoneAlias_success_savesAlias() {
        RegisterPhoneAliasRequest request = new RegisterPhoneAliasRequest(
                PHONE, SENDER_IBAN, SENDER_WALLET
        );

        service.registerPhoneAlias(request, "user-001");

        verify(phoneAliasRepository).save(any(PhoneAlias.class));
        verify(auditLogRepository).save(any());
    }

    @Test
    @DisplayName("TC-P2P-02: registerPhoneAlias — Konto nicht vorhanden → NoSuchElementException")
    void registerPhoneAlias_accountNotFound_throwsNotFound() {
        when(accountRepository.findByIban(SENDER_IBAN)).thenReturn(Optional.empty());

        RegisterPhoneAliasRequest request = new RegisterPhoneAliasRequest(
                PHONE, SENDER_IBAN, SENDER_WALLET
        );

        assertThatThrownBy(() -> service.registerPhoneAlias(request, "user-001"))
                .isInstanceOf(java.util.NoSuchElementException.class)
                .hasMessageContaining(SENDER_IBAN);
    }

    // ─── TC-P2P-03: P2P-Zahlung senden ──────────────────────────────────────

    @Test
    @DisplayName("TC-P2P-03: sendToPhone — Happy Path → SETTLED Transaktion")
    void sendToPhone_happyPath_returnsSettled() {
        P2pPhoneRequest request = new P2pPhoneRequest(
                SENDER_IBAN, PHONE, new BigDecimal("25.00")
        );

        TransactionResponse response = service.sendToPhone(IDEM_KEY, request, "user-001");

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(TransactionStatus.SETTLED);
        verify(circleWalletClient).initiateTransfer(any());
        verify(auditLogRepository).save(any());
    }

    @Test
    @DisplayName("TC-P2P-04: sendToPhone — Empfänger-Nummer nicht registriert → NoSuchElementException")
    void sendToPhone_unknownRecipient_throwsNotFound() {
        when(phoneAliasRepository.findByPhoneNumberHash(PHONE_HASH)).thenReturn(Optional.empty());

        P2pPhoneRequest request = new P2pPhoneRequest(
                SENDER_IBAN, PHONE, new BigDecimal("25.00"), "Test"
        );

        assertThatThrownBy(() -> service.sendToPhone(IDEM_KEY, request, "user-001"))
                .isInstanceOf(java.util.NoSuchElementException.class)
                .hasMessageContaining(PHONE);
    }

    @Test
    @DisplayName("TC-P2P-05: sendToPhone — Absender-Konto nicht gefunden → NoSuchElementException")
    void sendToPhone_senderAccountNotFound_throwsNotFound() {
        when(accountRepository.findByIban(SENDER_IBAN)).thenReturn(Optional.empty());

        P2pPhoneRequest request = new P2pPhoneRequest(
                SENDER_IBAN, PHONE, new BigDecimal("25.00"), "Test"
        );

        assertThatThrownBy(() -> service.sendToPhone(IDEM_KEY, request, "user-001"))
                .isInstanceOf(java.util.NoSuchElementException.class)
                .hasMessageContaining(SENDER_IBAN);
    }

    @Test
    @DisplayName("TC-P2P-06: sendToPhone — doppelter Idempotency-Key → IdempotencyConflictException")
    void sendToPhone_duplicateIdempotencyKey_throwsConflict() {
        StablecoinTransaction existingTx = new StablecoinTransaction();
        ReflectionTestUtils.setField(existingTx, "id", UUID.randomUUID());
        when(txRepository.findByIdempotencyKey(IDEM_KEY)).thenReturn(Optional.of(existingTx));

        P2pPhoneRequest request = new P2pPhoneRequest(
                SENDER_IBAN, PHONE, new BigDecimal("25.00"), "Test"
        );

        assertThatThrownBy(() -> service.sendToPhone(IDEM_KEY, request, "user-001"))
                .isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    @DisplayName("TC-P2P-07: sendToPhone — PhoneHashService korrekt aufgerufen")
    void sendToPhone_phoneHashServiceUsed() {
        P2pPhoneRequest request = new P2pPhoneRequest(
                SENDER_IBAN, PHONE, new BigDecimal("10.00")
        );

        service.sendToPhone(IDEM_KEY, request, "user-001");

        verify(phoneHashService).hash(PHONE);
    }

    @Test
    @DisplayName("TC-P2P-08: sendToPhone — Circle-Transfer an Empfänger-Wallet gerichtet")
    void sendToPhone_circleTransferToReceiverWallet() {
        P2pPhoneRequest request = new P2pPhoneRequest(
                SENDER_IBAN, PHONE, new BigDecimal("10.00")
        );

        service.sendToPhone(IDEM_KEY, request, "user-001");

        verify(circleWalletClient).initiateTransfer(
                argThat(req -> req.destination().address().equals(RECEIVER_WALLET))
        );
    }

    private static <T> T argThat(org.mockito.ArgumentMatcher<T> matcher) {
        return org.mockito.Mockito.argThat(matcher);
    }
}
