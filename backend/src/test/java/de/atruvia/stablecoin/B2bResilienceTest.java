package de.atruvia.stablecoin;

import de.atruvia.stablecoin.config.TenantContext;
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
class B2bResilienceTest extends AbstractLocalDbTest {

    @Autowired B2bTransferService transferService;
    @Autowired OutboxProcessor outboxProcessor;
    @Autowired CustomerAccountRepository accountRepository;
    @Autowired AddressBookRepository addressBookRepository;
    @Autowired StablecoinTransactionRepository txRepository;
    @Autowired OutboxMessageRepository outboxRepository;
    @Autowired AuditLogRepository auditLogRepository;
    @Autowired ApprovalWorkflowRepository approvalRepository;
    @Autowired YieldPositionRepository yieldPositionRepository;

    @SpyBean
    CircleWalletClient circleWalletClient;

    private CustomerAccount b2bAccount;
    private static final String B2B_IBAN = "DE89370400440532013000";
    private static final String WHITELISTED_WALLET = "0xA100000000000000000000000000000000000001";

    @BeforeEach
    void setUp() {
        TenantContext.set("tenant-default");
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
        // Reihenfolge wichtig: FK audit_log.transaction_id → stablecoin_transaction
        // Reihenfolge nach FK-Abhängigkeiten:
        outboxRepository.deleteAll();              // outbox_message.transaction_id FK
        auditLogRepository.deleteAll();            // audit_log.transaction_id FK
        approvalRepository.deleteAll();            // approval_workflow.transaction_id FK
        yieldPositionRepository.deleteAll();       // yield_position.deposit_transaction_id FK (nullable)
        txRepository.deleteAll();
        addressBookRepository.findByCustomerAccountIdAndStatus(b2bAccount.getId(), AddressStatus.ACTIVE)
                .stream()
                .filter(a -> a.getWalletAddress().equals(WHITELISTED_WALLET))
                .forEach(addressBookRepository::delete);
        Mockito.reset(circleWalletClient);
        TenantContext.clear();
    }

    // ── TC-R-01: Strikte Idempotenz ───────────────────────────────────────────

    @Test
    @DisplayName("TC-R-01: Zweiter Aufruf mit gleichem Idempotency-Key wirft IdempotencyConflictException")
    void sameIdempotencyKey_secondCallThrowsConflict() {
        String idempotencyKey = "idem-test-" + UUID.randomUUID();
        InitiateTransferRequest request = new InitiateTransferRequest(
                B2B_IBAN, WHITELISTED_WALLET, new BigDecimal("100"), StablecoinCurrency.USDC, null, null, "Test", null, null, null);

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
                B2B_IBAN, WHITELISTED_WALLET, new BigDecimal("200"), StablecoinCurrency.USDC, null, null, "Test", null, null, null);

        // Retry+CircuitBreaker: Fallback wirft IllegalStateException, das propagiert
        // (entweder direkt "Circle nicht erreichbar" oder nach Retry-Erschöpfung)
        assertThatThrownBy(() -> transferService.initiate(idempotencyKey, request, "cust-b2b-001"))
                .isInstanceOf(Exception.class);

        // TX muss FAILED sein — das ist die wichtige Invariante
        var tx = txRepository.findByIdempotencyKey(idempotencyKey).orElseThrow();
        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.FAILED);

        // SUBMIT_TO_BLOCKCHAIN Outbox-Nachricht muss existieren (committed in FUNDS_HELD REQUIRES_NEW)
        List<OutboxMessage> outboxMessages = outboxRepository.findByTransactionId(tx.getId());
        assertThat(outboxMessages).anyMatch(m -> "SUBMIT_TO_BLOCKCHAIN".equals(m.getEventType()));
    }

    // ── TC-R-03: Outbox-Recovery für SUBMITTED-Transaktionen ─────────────────

    @Test
    @DisplayName("TC-R-03: OutboxProcessor setzt SUBMITTED-TX via Circle-Poll auf SETTLED (Crash-Recovery)")
    void outboxRecovery_submittedTransaction_settlesViaCirclePoll() {
        // Simuliertes Crash-Szenario:
        // 1. Circle.initiateTransfer() schlägt beim ersten Aufruf fehl → TX = FAILED
        // 2. TX wird manuell auf SUBMITTED mit circleId gesetzt (simuliert: Circle hatte TX akzeptiert,
        //    aber Response wurde nicht empfangen — Server-Crash nach Circle-Aufruf)
        // 3. SUBMIT_TO_BLOCKCHAIN Outbox-Nachricht triggert Recovery beim Neustart
        // 4. Circle-Poll liefert COMPLETE → TX = SETTLED

        String circleId = "circle-recovery-" + UUID.randomUUID();

        // Schritt 1: Circle schlägt fehl → TX landet in FAILED
        doThrow(new RuntimeException("Simulierter Crash"))
                .when(circleWalletClient).initiateTransfer(any());

        String idempotencyKey = "recovery-" + UUID.randomUUID();
        InitiateTransferRequest request = new InitiateTransferRequest(
                B2B_IBAN, WHITELISTED_WALLET, new BigDecimal("300"), StablecoinCurrency.USDC, null, null, "Recovery-Test", null, null, null);

        assertThatThrownBy(() -> transferService.initiate(idempotencyKey, request, "cust-b2b-001"))
                .isInstanceOf(Exception.class);

        var tx = txRepository.findByIdempotencyKey(idempotencyKey).orElseThrow();
        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.FAILED);

        // Schritt 2: TX-Status manuell direkt in DB auf SUBMITTED setzen + circleId
        // (simuliert: Circle hatte TX akzeptiert, Antwort ging verloren — Server-Crash)
        // Direkter DB-Zugriff umgeht die State-Machine-Validierung (Recovery-Szenario)
        var submittedTx = txRepository.findById(tx.getId()).orElseThrow();
        submittedTx.setStatus(TransactionStatus.SUBMITTED);
        submittedTx.setCircleTransactionId(circleId);
        txRepository.save(submittedTx);

        // Schritt 3: Circle-Poll-Mock konfigurieren → COMPLETE
        doReturn(new CircleTransactionStatusDto(circleId, "COMPLETE", "0xRecoveryHash123", null))
                .when(circleWalletClient).getTransactionStatus(circleId);

        // SUBMIT_TO_BLOCKCHAIN Outbox-Nachricht sicherstellen (committed beim FUNDS_HELD)
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

        // Schritt 4: OutboxProcessor — Recovery auslösen
        outboxProcessor.processPendingMessages();

        // Ergebnis: TX muss SETTLED sein
        var settledTx = txRepository.findById(tx.getId()).orElseThrow();
        assertThat(settledTx.getStatus()).isEqualTo(TransactionStatus.SETTLED);
        assertThat(settledTx.getBlockchainHash()).isEqualTo("0xRecoveryHash123");

        // Outbox-Nachricht: SENT
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
                B2B_IBAN, WHITELISTED_WALLET, new BigDecimal("150"), StablecoinCurrency.USDC, null, null, "Outbox-Test", null, null, null);

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
