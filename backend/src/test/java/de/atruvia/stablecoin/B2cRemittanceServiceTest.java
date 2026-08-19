package de.atruvia.stablecoin;

import de.atruvia.stablecoin.client.ChainalysisClient;
import de.atruvia.stablecoin.client.CircleWalletClient;
import de.atruvia.stablecoin.client.dto.AddressScreenRequestDto;
import de.atruvia.stablecoin.client.dto.AddressScreenResponseDto;
import de.atruvia.stablecoin.client.dto.CircleTransferResponseDto;
import de.atruvia.stablecoin.dto.request.b2c.RemittanceRequest;
import de.atruvia.stablecoin.dto.response.RemittanceResponse;
import de.atruvia.stablecoin.entity.CustomerAccount;
import de.atruvia.stablecoin.entity.StablecoinTransaction;
import de.atruvia.stablecoin.entity.TransactionStatus;
import de.atruvia.stablecoin.exception.ComplianceBlockException;
import de.atruvia.stablecoin.exception.IdempotencyConflictException;
import de.atruvia.stablecoin.repository.AuditLogRepository;
import de.atruvia.stablecoin.repository.CustomerAccountRepository;
import de.atruvia.stablecoin.repository.StablecoinTransactionRepository;
import de.atruvia.stablecoin.service.b2c.B2cRemittanceService;
import de.atruvia.stablecoin.service.revenue.RevenueService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit-Tests für B2cRemittanceService — kein Spring-Kontext.
 */
class B2cRemittanceServiceTest {

    @Mock StablecoinTransactionRepository txRepository;
    @Mock CustomerAccountRepository accountRepository;
    @Mock AuditLogRepository auditLogRepository;
    @Mock CircleWalletClient circleWalletClient;
    @Mock ChainalysisClient chainalysisClient;
    @Mock RevenueService revenueService;

    @InjectMocks B2cRemittanceService service;

    private static final String IBAN = "DE89370400440532013000";
    private static final String WALLET = "0xCustomerWallet";
    private static final String IDEM_KEY = UUID.randomUUID().toString();

    private CustomerAccount account;
    private AddressScreenResponseDto approved;
    private AddressScreenResponseDto rejected;
    private CircleTransferResponseDto circleOk;
    private RevenueService.RevenueCalculation revenue;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        ReflectionTestUtils.setField(service, "feeB2c", new BigDecimal("0.50"));

        account = new CustomerAccount();
        account.setIban(IBAN);
        account.setWalletAddress(WALLET);

        approved = new AddressScreenResponseDto(WALLET, "LOW", Collections.emptyList(), false, true);
        rejected = new AddressScreenResponseDto(WALLET, "CRITICAL", Collections.emptyList(), true, false);

        circleOk = new CircleTransferResponseDto("circle-tx-001", "COMPLETE", "0xhash", null);

        revenue = new RevenueService.RevenueCalculation(
                new BigDecimal("0.0015"), new BigDecimal("1.50"),
                new BigDecimal("0.50"), new BigDecimal("0.008"), new BigDecimal("2.00")
        );

        StablecoinTransaction savedTx = new StablecoinTransaction();
        ReflectionTestUtils.setField(savedTx, "id", UUID.randomUUID());
        savedTx.setStatus(TransactionStatus.SETTLED);

        when(txRepository.findByIdempotencyKey(IDEM_KEY)).thenReturn(Optional.empty());
        when(accountRepository.findByIban(IBAN)).thenReturn(Optional.of(account));
        when(chainalysisClient.screenAddress(any())).thenReturn(approved);
        when(circleWalletClient.initiateTransfer(any())).thenReturn(circleOk);
        when(txRepository.save(any())).thenReturn(savedTx);
        when(auditLogRepository.save(any())).thenReturn(null);
        when(revenueService.calculate(any(), any())).thenReturn(revenue);
    }

    // ─── TC-REM-01: Happy Path ────────────────────────────────────────────────

    @Test
    @DisplayName("TC-REM-01: Erfolgreiche Überweisung → SETTLED mit Tracking-Code")
    void send_happyPath_returnsSettledResponse() {
        RemittanceRequest request = new RemittanceRequest(
                IBAN, "+521234567890", new BigDecimal("100.00"), "MX", "Juan García"
        );

        RemittanceResponse response = service.send(IDEM_KEY, request, "user-b2c-001");

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo("SETTLED");
        assertThat(response.trackingCode()).startsWith("ATR-");
        assertThat(response.feeEur()).isEqualByComparingTo("0.50");
        verify(auditLogRepository).save(any());
    }

    // ─── TC-REM-02: Compliance-Block ─────────────────────────────────────────

    @Test
    @DisplayName("TC-REM-02: Chainalysis blockiert Wallet → ComplianceBlockException")
    void send_complianceRejected_throwsComplianceBlock() {
        when(chainalysisClient.screenAddress(any())).thenReturn(rejected);

        RemittanceRequest request = new RemittanceRequest(
                IBAN, "+521234567890", new BigDecimal("100.00"), "MX", "Juan García"
        );

        assertThatThrownBy(() -> service.send(IDEM_KEY, request, "user-b2c-001"))
                .isInstanceOf(ComplianceBlockException.class);

        verify(circleWalletClient, never()).initiateTransfer(any());
        verify(txRepository, never()).save(any());
    }

    // ─── TC-REM-03: Idempotenz-Konflikt ──────────────────────────────────────

    @Test
    @DisplayName("TC-REM-03: Doppelter Idempotency-Key → IdempotencyConflictException")
    void send_duplicateIdempotencyKey_throwsConflict() {
        StablecoinTransaction existingTx = new StablecoinTransaction();
        ReflectionTestUtils.setField(existingTx, "id", UUID.randomUUID());
        when(txRepository.findByIdempotencyKey(IDEM_KEY)).thenReturn(Optional.of(existingTx));

        RemittanceRequest request = new RemittanceRequest(
                IBAN, "+521234567890", new BigDecimal("100.00"), "MX", "Juan García"
        );

        assertThatThrownBy(() -> service.send(IDEM_KEY, request, "user-b2c-001"))
                .isInstanceOf(IdempotencyConflictException.class);
    }

    // ─── TC-REM-04: Konto nicht gefunden ─────────────────────────────────────

    @Test
    @DisplayName("TC-REM-04: IBAN nicht gefunden → NoSuchElementException")
    void send_accountNotFound_throwsNotFound() {
        when(accountRepository.findByIban(IBAN)).thenReturn(Optional.empty());

        RemittanceRequest request = new RemittanceRequest(
                IBAN, "+521234567890", new BigDecimal("100.00"), "MX", "Juan García"
        );

        assertThatThrownBy(() -> service.send(IDEM_KEY, request, "user-b2c-001"))
                .isInstanceOf(java.util.NoSuchElementException.class)
                .hasMessageContaining(IBAN);
    }

    // ─── TC-REM-05: Länder-Währungs-Mapping ──────────────────────────────────

    @ParameterizedTest(name = "Land={0} → Währung={1}")
    @CsvSource({
            "MX, MXN",
            "PH, PHP",
            "IN, INR",
            "NG, NGN",
            "US, USD",
            "DE, USD"
    })
    @DisplayName("TC-REM-05: Ländercodes werden korrekt in Zielwährungen gemappt")
    void send_countryMapping_correctCurrencyInResponse(String country, String expectedCurrency) {
        RemittanceRequest request = new RemittanceRequest(
                IBAN, "+1234567890", new BigDecimal("50.00"), country, "Test Recipient"
        );

        RemittanceResponse response = service.send(IDEM_KEY, request, "user-001");

        assertThat(response.recipientReceivesApprox()).contains(expectedCurrency);
    }

    // ─── TC-REM-06: Chainalysis-Request hat korrekten Typ ────────────────────

    @Test
    @DisplayName("TC-REM-06: Chainalysis-Screening mit korrekten Parametern aufgerufen")
    void send_chainalysisCalledWithCorrectParams() {
        RemittanceRequest request = new RemittanceRequest(
                IBAN, "+521234567890", new BigDecimal("100.00"), "MX", "Juan García"
        );

        service.send(IDEM_KEY, request, "user-001");

        verify(chainalysisClient).screenAddress(
                eq(new AddressScreenRequestDto(WALLET, "USDC", "MATIC", "outgoing"))
        );
    }

    // ─── TC-REM-07: Circle-Transfer mit korrekten Beträgen ───────────────────

    @Test
    @DisplayName("TC-REM-07: Circle-Transfer wird mit korrektem EUR-Betrag initiiert")
    void send_circleCalledWithCorrectAmount() {
        BigDecimal amount = new BigDecimal("250.00");
        RemittanceRequest request = new RemittanceRequest(
                IBAN, "+521234567890", amount, "MX", "Maria Gonzalez"
        );

        service.send(IDEM_KEY, request, "user-001");

        verify(circleWalletClient).initiateTransfer(
                argThat(req -> req.amount().amount().equals("250.00"))
        );
    }

    private static <T> T argThat(org.mockito.ArgumentMatcher<T> matcher) {
        return org.mockito.Mockito.argThat(matcher);
    }
}
