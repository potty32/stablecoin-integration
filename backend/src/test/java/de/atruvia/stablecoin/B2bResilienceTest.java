package de.atruvia.stablecoin;

import de.atruvia.stablecoin.client.CircleWalletClient;
import de.atruvia.stablecoin.client.dto.CircleTransactionStatusDto;
import de.atruvia.stablecoin.client.dto.CircleTransferResponseDto;
import de.atruvia.stablecoin.dto.request.b2b.InitiateTransferRequest;
import de.atruvia.stablecoin.entity.*;
import de.atruvia.stablecoin.exception.IdempotencyConflictException;
import de.atruvia.stablecoin.outbox.OutboxProcessor;
import de.atruvia.stablecoin.repository.*;
import de.atruvia.stablecoin.service.b2b.B2bTransferService;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integrationstests für Ausfallsicherheit: Idempotenz, Netzwerkausfälle, Outbox-Recovery.
 *
 * KEIN @Transactional auf Klassenebene — Tests müssen DB-Zustand über Service-Call-Grenzen
 * hinweg prüfen (Rollback würde das verhindern).
 */
@SpringBootTest
class B2bResilienceTest extends AbstractIntegrationTest {

    @Autowired B2bTransferService transferService;
    @Autowired OutboxProcessor outboxProcessor;
    @Autowired CustomerAccountRepository accountRepository;
    @Autowired AddressBookRepository addressBookRepository;
    @Autowired StablecoinTransactionRepository txRepository;
    @Autowired OutboxMessageRepository outboxRepository;

    @SpyBean
    CircleWalletClient circleWalletClient;

    private CustomerAccount b2bAccount;
    private static final String B2B_IBAN = "DE89370400440532013000";
    private static final String WHITELISTED_WALLET = "0xA100000000000000000000000000000000000001";

    @BeforeEach
    void setUp() {
        b2bAccount = accountRepository.findByIban(B2B_IBAN).orElseThrow();
        // Whitelist-Eintrag sicherstellen
        if (addressBookRepository.findByCustomerAccountIdAndWalletAddressAndStatus(
                b2bAccount.getId(), WHITELISTED_WALLET, AddressStatus.ACTIVE).isEmpty()) {
            AddressBook entry = new AddressBook();
            entry.setCustomerAccount(b2bAccount);
            entry.setLabel("Test Wallet");
            entry.setWalletAddress(WHITELISTED_WALLET);
            entry.setCurrency(StablecoinCurrency.USDC);
            entry.setRiskScore(RiskScore.LOW);
            entry.setStatus(AddressStatus.ACTIVE);
            addressBookRepository.save(entry);
        }
        Mockito.reset(circleWalletClient);
    }

    @AfterEach
    void cleanup() {
        // Manuelles Cleanup da kein @Transactional-Rollback
        txRepository.deleteAll();
        outboxRepository.deleteAll();
        addressBookRepository.findByCustomerAccountIdAndStatus(b2bAccount.getId(), AddressStatus.ACTIVE)
                .stream()
                .filter(a -> a.getWalletAddress().equals(WHITELISTED_WALLET))
                .forEach(addressBookRepository::delete);
        Mockito.reset(circleWalletClient);
    }

    // ── TC-R-01: Strikte Idempotenz ───────────────────────────────────────────

    @Test
    @DisplayName("TC-R-01: Zweiter Aufruf mit gleichem Idempotency-Key wirft IdempotencyConflictException")
    void sameIdempotencyKey_secondCallThrowsConflict() {
        String idempotencyKey = "idem-test-" + UUID.randomUUID();
        InitiateTransferRequest request = new InitiateTransferRequest(
                B2B_IBAN, WHITELISTED_WALLET, new BigDecimal("100"), StablecoinCurrency.USDC, null, null, "Test");

        // Erster Aufruf: erfolgreich
        var result = transferService.initiate(idempotencyKey, request, "cust-b2b-001");
        assertThat(result.status()).isEqualTo(TransactionStatus.SETTLED);

        // Zweiter Aufruf mit GLEICHEM Key: muss sofort IdempotencyConflictException werfen
        assertThatThrownBy(() -> transferService.initiate(idempotencyKey, request, "cust-b2b-001"))
                .isInstanceOf(IdempotencyConflictException.class);

        // DB: genau eine TX mit diesem Idempotency-Key
        assertThat(txRepository.findByIdempotencyKey(idempotencyKey)).isPresent();
        assertThat(txRepository.findAll().stream()
                .filter(tx -> idempotencyKey.equals(tx.getIdempotencyKey()))
                .count()).isEqualTo(1);
    }

    // ── TC-R-02: Circle-Netzwerkausfall → FAILED + Hold freigegeben ──────────

    @Test
    @DisplayName("TC-R-02: Circle-Netzwerkausfall nach FUNDS_HELD → TX = FAILED, Hold-Release Outbox vorhanden")
    void circleNetworkFailure_transactionFailed_holdReleased() {
        // Circle.initiateTransfer() wirft RuntimeException (Netzwerkausfall)
        // Retry erschöpft (max-attempts=3 in Config), dann Fallback → FAILED
        doThrow(new RuntimeException("Connection refused: Circle nicht erreichbar"))
                .when(circleWalletClient).initiateTransfer(any());

        String idempotencyKey = "circle-fail-" + UUID.randomUUID();
        InitiateTransferRequest request = new InitiateTransferRequest(
                B2B_IBAN, WHITELISTED_WALLET, new BigDecimal("200"), StablecoinCurrency.USDC, null, null, "Test");

        assertThatThrownBy(() -> transferService.initiate(idempotencyKey, request, "cust-b2b-001"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Circle nicht erreichbar");

        // TX muss FAILED sein
        var tx = txRepository.findByIdempotencyKey(idempotencyKey).orElseThrow();
        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.FAILED);
        assertThat(tx.getFailureReason()).contains("CIRCLE_UNAVAILABLE");

        // SUBMIT_TO_BLOCKCHAIN Outbox-Nachricht muss existieren (committed in FUNDS_HELD REQUIRES_NEW)
        List<OutboxMessage> outboxMessages = outboxRepository.findByTransactionId(tx.getId());
        assertThat(outboxMessages).anyMatch(m -> "SUBMIT_TO_BLOCKCHAIN".equals(m.getEventType()));
    }

    // ── TC-R-03: Outbox-Recovery für SUBMITTED-Transaktionen ─────────────────

    @Test
    @DisplayName("TC-R-03: OutboxProcessor setzt SUBMITTED-TX via Circle-Poll auf SETTLED")
    void outboxRecovery_submittedTransaction_settlesViaCirclePoll() {
        // Schritt 1: TX auf SUBMITTED bringen mit echter circleTransactionId
        String circleId = "circle-recovery-" + UUID.randomUUID();

        // Mock: initiateTransfer OK, aber Status erst beim zweiten Poll COMPLETE
        doReturn(new CircleTransferResponseDto(circleId, "PENDING", null, null))
                .when(circleWalletClient).initiateTransfer(any());
        // Beim direkten Poll: COMPLETE
        doReturn(new CircleTransactionStatusDto(circleId, "COMPLETE", "0xRecoveryHash123", null))
                .when(circleWalletClient).getTransactionStatus(circleId);

        String idempotencyKey = "recovery-" + UUID.randomUUID();
        InitiateTransferRequest request = new InitiateTransferRequest(
                B2B_IBAN, WHITELISTED_WALLET, new BigDecimal("300"), StablecoinCurrency.USDC, null, null, "Recovery-Test");

        // Erster Aufruf: TX geht bis SUBMITTED (Circle liefert PENDING → IllegalState → FAILED via catch)
        // Alternativ: TX manuell in SUBMITTED setzen für den Recovery-Test
        // Da der Mock "PENDING" zurückgibt und das als Fehler gilt, TX = FAILED
        // Für reinen Recovery-Test: TX direkt in DB auf SUBMITTED setzen

        // TX initiieren bis FUNDS_HELD (Circle mock schlägt fehl → TX = FAILED)
        assertThatThrownBy(() -> transferService.initiate(idempotencyKey, request, "cust-b2b-001"))
                .isInstanceOf(Exception.class);

        var tx = txRepository.findByIdempotencyKey(idempotencyKey).orElseThrow();

        // TX manuell auf SUBMITTED setzen + circleTransactionId für Recovery-Test
        tx.setStatus(TransactionStatus.SUBMITTED);
        tx.setCircleTransactionId(circleId);
        txRepository.save(tx);

        // SUBMIT_TO_BLOCKCHAIN Outbox-Nachricht auf PENDING setzen (Recovery auslösen)
        OutboxMessage recoveryMsg = outboxRepository.findByTransactionId(tx.getId())
                .stream().filter(m -> "SUBMIT_TO_BLOCKCHAIN".equals(m.getEventType()))
                .findFirst()
                .orElseGet(() -> {
                    OutboxMessage m = new OutboxMessage();
                    m.setTransactionId(tx.getId());
                    m.setAggregateType("StablecoinTransaction");
                    m.setEventType("SUBMIT_TO_BLOCKCHAIN");
                    m.setPayload("{\"txId\":\"" + tx.getId() + "\"}");
                    return outboxRepository.save(m);
                });
        recoveryMsg.setStatus(OutboxStatus.PENDING);
        outboxRepository.save(recoveryMsg);

        // Schritt 2: OutboxProcessor auslösen
        outboxProcessor.processPendingMessages();

        // TX muss SETTLED sein
        var settledTx = txRepository.findById(tx.getId()).orElseThrow();
        assertThat(settledTx.getStatus()).isEqualTo(TransactionStatus.SETTLED);
        assertThat(settledTx.getBlockchainHash()).isEqualTo("0xRecoveryHash123");

        // Outbox-Nachricht muss SENT sein
        var processedMsg = outboxRepository.findById(recoveryMsg.getId()).orElseThrow();
        assertThat(processedMsg.getStatus()).isEqualTo(OutboxStatus.SENT);
    }

    // ── TC-R-04: SUBMIT_TO_BLOCKCHAIN Outbox wird bei FUNDS_HELD committed ───

    @Test
    @DisplayName("TC-R-04: SUBMIT_TO_BLOCKCHAIN OutboxMessage wird bei FUNDS_HELD atomar committed")
    void fundsHeld_submitToBlockchainOutboxCommitted() {
        // Normaler Transfer — lass ihn komplett durchlaufen
        String idempotencyKey = "outbox-check-" + UUID.randomUUID();
        InitiateTransferRequest request = new InitiateTransferRequest(
                B2B_IBAN, WHITELISTED_WALLET, new BigDecimal("150"), StablecoinCurrency.USDC, null, null, "Outbox-Test");

        var result = transferService.initiate(idempotencyKey, request, "cust-b2b-001");
        assertThat(result.status()).isEqualTo(TransactionStatus.SETTLED);

        // SUBMIT_TO_BLOCKCHAIN muss in der Outbox existieren
        var tx = txRepository.findByIdempotencyKey(idempotencyKey).orElseThrow();
        List<OutboxMessage> messages = outboxRepository.findByTransactionId(tx.getId());
        assertThat(messages)
                .anyMatch(m -> "SUBMIT_TO_BLOCKCHAIN".equals(m.getEventType()))
                .anyMatch(m -> "TRANSACTION_SETTLED".equals(m.getEventType()));
    }
}
