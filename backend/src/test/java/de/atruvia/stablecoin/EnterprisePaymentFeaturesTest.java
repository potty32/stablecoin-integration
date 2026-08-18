package de.atruvia.stablecoin;

import de.atruvia.stablecoin.config.TenantContext;
import de.atruvia.stablecoin.dto.request.InboundWebhookRequest;
import de.atruvia.stablecoin.dto.request.ReassignTransactionRequest;
import de.atruvia.stablecoin.dto.response.TransactionResponse;
import de.atruvia.stablecoin.entity.*;
import de.atruvia.stablecoin.repository.*;
import de.atruvia.stablecoin.service.b2b.ExportService;
import de.atruvia.stablecoin.service.b2b.ReassignTransactionService;
import de.atruvia.stablecoin.service.inbound.InboundProcessingService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integrationstests für Enterprise Payment Features (UC-29, UC-30, UC-31).
 *
 * TC1: CAMT.054-Export — SETTLED Inbound-TXs → gültiges XML mit CRDT-Einträgen
 * TC2: CAMT.054-Export (leer) — keine Inbound-TXs → valides XML ohne Ntry-Elemente
 * TC3: Automatische Retoure — gesperrtes Konto → Original-TX FAILED + INBOUND_RETURN RETURNED
 * TC4: Sammelkonto — unbekannte Wallet → TX mit UNASSIGNED-Status
 * TC5: Admin-Reassign — UNASSIGNED-TX → manuell zugeordnet → SETTLED + Ledger-Buchung
 */
class EnterprisePaymentFeaturesTest extends AbstractLocalDbTest {

    private static final String B2B_IBAN       = "DE89370400440532013000";
    private static final String B2B_WALLET     = "0xBankB2BWallet000000000000000000000000001";
    private static final String LOW_RISK_SENDER = "0xA100000000000000000000000000000000000001";
    private static final String UNKNOWN_WALLET  = "0xUNKNOWN0000000000000000000000000000001";

    @Autowired InboundProcessingService inboundProcessingService;
    @Autowired ExportService exportService;
    @Autowired ReassignTransactionService reassignTransactionService;
    @Autowired CustomerAccountRepository accountRepository;
    @Autowired StablecoinTransactionRepository txRepository;
    @Autowired AuditLogRepository auditLogRepository;
    @Autowired OutboxMessageRepository outboxRepository;
    @Qualifier("adminJdbcTemplate")
    @Autowired JdbcTemplate adminJdbcTemplate;

    private CustomerAccount b2bAccount;

    @BeforeEach
    void setUp() {
        TenantContext.set("tenant-default");
        b2bAccount = accountRepository.findByIban(B2B_IBAN).orElseThrow();
        // Pre-Cleanup: Sicherstellen dass der vorherige Test seine Daten vollständig entfernt hat
        adminJdbcTemplate.update("UPDATE stablecoin_transaction SET parent_transaction_id = NULL");
        adminJdbcTemplate.update("DELETE FROM audit_log");
        adminJdbcTemplate.update("DELETE FROM outbox_message");
        adminJdbcTemplate.update("DELETE FROM approval_workflow");
        adminJdbcTemplate.update("DELETE FROM stablecoin_transaction");
    }

    @AfterEach
    void cleanup() {
        // Restore B2B account to ACTIVE
        b2bAccount.setStatus(AccountStatus.ACTIVE);
        accountRepository.save(b2bAccount);

        // Cleanup-Reihenfolge (FK-Abhängigkeiten):
        // 1. AuditLog hat FK → stablecoin_transaction
        // 2. Outbox hat FK → stablecoin_transaction
        // 3. approval_workflow hat FK → stablecoin_transaction
        // 4. parent_transaction_id ist self-referential FK → erst auf NULL setzen
        adminJdbcTemplate.update("UPDATE stablecoin_transaction SET parent_transaction_id = NULL");
        adminJdbcTemplate.update("DELETE FROM audit_log");
        adminJdbcTemplate.update("DELETE FROM outbox_message");
        adminJdbcTemplate.update("DELETE FROM approval_workflow");
        // Alle Test-TXs löschen (Seed-Daten existieren nicht in stablecoin_transaction)
        adminJdbcTemplate.update("DELETE FROM stablecoin_transaction");

        TenantContext.clear();
    }

    // ── TC1: CAMT.054-Export mit Inbound-Transaktionen ────────────────────────

    @Test
    @DisplayName("TC1: CAMT.054-Export — SETTLED Inbound-TXs werden als CRDT-Einträge exportiert")
    void camt054_settledInboundTxs_generatesValidXmlWithCrdtEntries() {
        // Arrange: 2 Inbound-TX via Webhook verarbeiten
        String hash1 = "0xcamt054-test-1-" + System.currentTimeMillis();
        String hash2 = "0xcamt054-test-2-" + System.currentTimeMillis();

        TransactionResponse resp1 = inboundProcessingService.processInbound(
                new InboundWebhookRequest(B2B_WALLET, BigDecimal.valueOf(500), "USDC", hash1, LOW_RISK_SENDER));
        TransactionResponse resp2 = inboundProcessingService.processInbound(
                new InboundWebhookRequest(B2B_WALLET, BigDecimal.valueOf(1000), "EURC", hash2, LOW_RISK_SENDER));

        assertThat(resp1.status()).isEqualTo(TransactionStatus.SETTLED);
        assertThat(resp2.status()).isEqualTo(TransactionStatus.SETTLED);

        // Act: CAMT.054 generieren
        String xml = exportService.generateCamt054(B2B_IBAN);

        // Assert: XML-Struktur
        assertThat(xml).contains("urn:iso:std:iso:20022:tech:xsd:camt.054.001.08");
        assertThat(xml).contains("BkToCstmrDbtCdtNtfctn");
        assertThat(xml).contains("Ntfctn");
        assertThat(xml).contains("CRDT");
        assertThat(xml).contains("BOOK");
        assertThat(xml).contains("RCDT");  // Received Credit Transfer
        assertThat(xml).contains(B2B_IBAN);
        assertThat(xml).contains(hash1);
        assertThat(xml).contains(hash2);
        // Mindestens 2 Ntry-Elemente
        long ntryCount = xml.split("<Ntry>").length - 1;
        assertThat(ntryCount).isGreaterThanOrEqualTo(2);
    }

    // ── TC2: CAMT.054-Export ohne Inbound-Transaktionen ──────────────────────

    @Test
    @DisplayName("TC2: CAMT.054-Export (leer) — valides XML auch ohne Inbound-TXs")
    void camt054_noInboundTxs_generatesValidEmptyXml() {
        String xml = exportService.generateCamt054(B2B_IBAN);

        assertThat(xml).contains("urn:iso:std:iso:20022:tech:xsd:camt.054.001.08");
        assertThat(xml).contains("BkToCstmrDbtCdtNtfctn");
        assertThat(xml).contains("Ntfctn");
        assertThat(xml).contains(B2B_IBAN);
        // Kein Ntry-Element bei leerer Liste
        assertThat(xml).doesNotContain("<Ntry>");
    }

    // ── TC3: Automatische Retoure bei gesperrtem Konto ────────────────────────

    @Test
    @DisplayName("TC3: Automatische Retoure — gesperrtes Konto → Original FAILED + INBOUND_RETURN RETURNED")
    void inboundReturn_suspendedAccount_createsReturnTransaction() {
        // Arrange: B2B-Konto sperren
        b2bAccount.setStatus(AccountStatus.SUSPENDED);
        accountRepository.save(b2bAccount);

        String hash = "0xreturn-test-" + System.currentTimeMillis();

        // Act: Inbound-Webhook verarbeiten
        TransactionResponse response = inboundProcessingService.processInbound(
                new InboundWebhookRequest(B2B_WALLET, BigDecimal.valueOf(750), "USDC", hash, LOW_RISK_SENDER));

        // Assert: Original-TX ist FAILED
        assertThat(response.status()).isEqualTo(TransactionStatus.FAILED);

        // Assert: INBOUND_RETURN-TX wurde erstellt und ist RETURNED
        // Gezielter Filter nach parentTransactionId = original TX ID (robust gegen stale Daten)
        UUID originalTxId = response.transactionId();
        List<StablecoinTransaction> allTxs = txRepository.findAll();
        StablecoinTransaction returnTx = allTxs.stream()
                .filter(tx -> tx.getType() == TransactionType.INBOUND_RETURN
                        && originalTxId.equals(tx.getParentTransactionId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "INBOUND_RETURN-TX mit parentTransactionId=" + originalTxId + " nicht gefunden"));

        assertThat(returnTx.getStatus()).isEqualTo(TransactionStatus.RETURNED);
        assertThat(returnTx.getDestinationWallet()).isEqualTo(LOW_RISK_SENDER);
        assertThat(returnTx.getAmountFiat()).isEqualByComparingTo(BigDecimal.valueOf(750));

        // AuditLog enthält Retoure-Eintrag
        List<AuditLog> logs = auditLogRepository.findAll();
        boolean hasReturnLog = logs.stream()
                .anyMatch(l -> "STATUS_CHANGE".equals(l.getAction())
                        && l.getToStatus() == TransactionStatus.RETURNED);
        assertThat(hasReturnLog).isTrue();
    }

    // ── TC4: Sammelkonto — unbekannte Wallet-Adresse ─────────────────────────

    @Test
    @DisplayName("TC4: Sammelkonto — unbekannte Wallet wird als UNASSIGNED auf Sammelkonto gebucht")
    void unassignedInbound_unknownWallet_parkedOnCollectionAccount() {
        String hash = "0xunassigned-test-" + System.currentTimeMillis();

        // Act: Webhook mit unbekannter Wallet
        TransactionResponse response = inboundProcessingService.processInbound(
                new InboundWebhookRequest(UNKNOWN_WALLET, BigDecimal.valueOf(200), "EURC", hash, LOW_RISK_SENDER));

        // Assert: TX auf Sammelkonto mit UNASSIGNED-Status
        assertThat(response.status()).isEqualTo(TransactionStatus.UNASSIGNED);

        // Sammelkonto-TX in DB verifizieren
        StablecoinTransaction tx = txRepository.findById(response.transactionId()).orElseThrow();
        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.UNASSIGNED);
        assertThat(tx.getCustomerAccount().getCustomerId()).isEqualTo("unassigned-funds");
        assertThat(tx.getDestinationWallet()).isEqualTo(UNKNOWN_WALLET);
        assertThat(tx.getAmountFiat()).isEqualByComparingTo(BigDecimal.valueOf(200));
        assertThat(tx.getBlockchainHash()).isEqualTo(hash);

        // AuditLog-Eintrag vorhanden
        List<AuditLog> logs = auditLogRepository.findAll();
        boolean hasUnassignedLog = logs.stream()
                .anyMatch(l -> "UNASSIGNED_INBOUND".equals(l.getAction()));
        assertThat(hasUnassignedLog).isTrue();
    }

    // ── TC5: Admin-Reassign — UNASSIGNED TX manuell zugeordnet ───────────────

    @Test
    @DisplayName("TC5: Admin-Reassign — UNASSIGNED-TX wird SETTLED + Ledger-Gutschrift anlegen")
    void reassignTransaction_unassignedTx_settlesOnTargetAccount() {
        // Arrange: UNASSIGNED-TX anlegen
        String hash = "0xreassign-test-" + System.currentTimeMillis();
        TransactionResponse unassignedResp = inboundProcessingService.processInbound(
                new InboundWebhookRequest(UNKNOWN_WALLET, BigDecimal.valueOf(300), "USDC", hash, LOW_RISK_SENDER));
        assertThat(unassignedResp.status()).isEqualTo(TransactionStatus.UNASSIGNED);
        UUID txId = unassignedResp.transactionId();

        // Act: Admin-Reassign auf B2B-Konto
        TransactionResponse settledResp = reassignTransactionService.reassign(
                new ReassignTransactionRequest(txId, B2B_IBAN));

        // Assert: TX ist jetzt SETTLED
        assertThat(settledResp.status()).isEqualTo(TransactionStatus.SETTLED);
        assertThat(settledResp.transactionId()).isEqualTo(txId);

        // TX in DB: customer_account ist jetzt B2B-Konto
        StablecoinTransaction tx = txRepository.findById(txId).orElseThrow();
        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.SETTLED);
        assertThat(tx.getCustomerAccount().getIban()).isEqualTo(B2B_IBAN);
        assertThat(tx.getSettledAt()).isNotNull();

        // AuditLog enthält MANUAL_REASSIGN-Eintrag
        List<AuditLog> logs = auditLogRepository.findAll();
        boolean hasReassignLog = logs.stream()
                .anyMatch(l -> "MANUAL_REASSIGN".equals(l.getAction())
                        && l.getToStatus() == TransactionStatus.SETTLED);
        assertThat(hasReassignLog).isTrue();
    }
}
