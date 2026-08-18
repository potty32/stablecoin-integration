package de.atruvia.stablecoin.service.b2b;

import de.atruvia.stablecoin.client.CoreBankingClient;
import de.atruvia.stablecoin.client.dto.LedgerBookingDto;
import de.atruvia.stablecoin.config.TenantContext;
import de.atruvia.stablecoin.dto.request.ReassignTransactionRequest;
import de.atruvia.stablecoin.dto.response.TransactionResponse;
import de.atruvia.stablecoin.entity.AuditLog;
import de.atruvia.stablecoin.entity.CustomerAccount;
import de.atruvia.stablecoin.entity.TransactionStatus;
import de.atruvia.stablecoin.repository.AuditLogRepository;
import de.atruvia.stablecoin.repository.CustomerAccountRepository;
import de.atruvia.stablecoin.repository.StablecoinTransactionRepository;
import de.atruvia.stablecoin.entity.StablecoinTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * UC-31: Manuelle Sammelkonto-Bereinigung.
 * Ein Bank-Sachbearbeiter ordnet eine UNASSIGNED-Transaktion einem echten Kundenkonto zu.
 * Stößt nachträglich die reguläre Ledger-Gutschrift an.
 */
@Service
public class ReassignTransactionService {

    private static final Logger log = LoggerFactory.getLogger(ReassignTransactionService.class);

    private final JdbcTemplate adminJdbcTemplate;
    private final StablecoinTransactionRepository txRepository;
    private final CustomerAccountRepository accountRepository;
    private final CoreBankingClient coreBankingClient;
    private final AuditLogRepository auditLogRepository;
    private final B2bTransferService transferService;

    public ReassignTransactionService(
            @Qualifier("adminJdbcTemplate") JdbcTemplate adminJdbcTemplate,
            StablecoinTransactionRepository txRepository,
            CustomerAccountRepository accountRepository,
            CoreBankingClient coreBankingClient,
            AuditLogRepository auditLogRepository,
            B2bTransferService transferService) {
        this.adminJdbcTemplate = adminJdbcTemplate;
        this.txRepository = txRepository;
        this.accountRepository = accountRepository;
        this.coreBankingClient = coreBankingClient;
        this.auditLogRepository = auditLogRepository;
        this.transferService = transferService;
    }

    /**
     * Ordnet eine UNASSIGNED-Transaktion einem Ziel-Konto zu und erstellt die Ledger-Gutschrift.
     *
     * Flow:
     * 1. TX via adminJdbcTemplate (BYPASSRLS) laden und validieren
     * 2. Ziel-Konto via adminJdbcTemplate laden
     * 3. TX auf Ziel-Konto + Ziel-Tenant umbuchen
     * 4. Ledger-Gutschrift via CoreBankingClient anlegen
     * 5. TX-Status → SETTLED
     */
    @Transactional
    public TransactionResponse reassign(ReassignTransactionRequest request) {
        UUID txId = request.transactionId();
        String targetIban = request.targetIban();

        log.info("[REASSIGN] Starte manuelle Zuordnung: txId={} targetIban={}", txId, targetIban);

        // 1. TX-Grunddaten via BYPASSRLS lesen (tenant_id unbekannt)
        List<java.util.Map<String, Object>> txRows = adminJdbcTemplate.queryForList(
                "SELECT id, status, amount_fiat, currency, tenant_id, customer_account_id " +
                "FROM stablecoin_transaction WHERE id = ?", txId);

        if (txRows.isEmpty()) {
            throw new NoSuchElementException("Transaktion nicht gefunden: " + txId);
        }
        java.util.Map<String, Object> txRow = txRows.get(0);
        String currentStatus = (String) txRow.get("status");

        if (!TransactionStatus.UNASSIGNED.name().equals(currentStatus)) {
            throw new IllegalStateException(
                    "Nur UNASSIGNED-Transaktionen können zugeordnet werden — aktuelle Status: " + currentStatus);
        }

        BigDecimal amountFiat = (BigDecimal) txRow.get("amount_fiat");
        String currency = (String) txRow.get("currency");

        // 2. Ziel-Konto via adminJdbc laden (egal welcher Tenant)
        List<java.util.Map<String, Object>> accountRows = adminJdbcTemplate.queryForList(
                "SELECT id, tenant_id, customer_id FROM customer_account WHERE iban = ?", targetIban);

        if (accountRows.isEmpty()) {
            throw new NoSuchElementException("Ziel-Konto nicht gefunden: " + targetIban);
        }
        java.util.Map<String, Object> accountRow = accountRows.get(0);
        UUID targetAccountId = (UUID) accountRow.get("id");
        String targetTenantId = (String) accountRow.get("tenant_id");
        String targetCustomerId = (String) accountRow.get("customer_id");

        // 3. TX-Update via adminJdbc (Tenant-übergreifend, BYPASSRLS)
        int updated = adminJdbcTemplate.update(
                "UPDATE stablecoin_transaction SET " +
                "customer_account_id = ?, tenant_id = ?, status = 'SETTLED', settled_at = NOW(), " +
                "updated_at = NOW() WHERE id = ? AND status = 'UNASSIGNED'",
                targetAccountId, targetTenantId, txId);

        if (updated == 0) {
            throw new IllegalStateException("TX-Update fehlgeschlagen (Race Condition?) — txId=" + txId);
        }

        // 4. Ledger-Gutschrift via CoreBankingClient (nachträgliche Buchung)
        coreBankingClient.createLedgerBooking(new LedgerBookingDto(
                "reassign-" + txId,
                "BANK_STABLECOIN_TRANSIT_ACCOUNT",
                List.of(new LedgerBookingDto.CreditEntry(targetIban, amountFiat, "REASSIGN_CREDIT")),
                amountFiat, "EUR", LocalDate.now()
        ));
        log.info("[REASSIGN] Ledger-Gutschrift: txId={} iban={} amount={}", txId, targetIban, amountFiat);

        // 5. AuditLog-Eintrag (im Kontext des Ziel-Tenants)
        TenantContext.set(targetTenantId);
        try {
            AuditLog auditEntry = new AuditLog();
            auditEntry.setTransactionId(txId);
            auditEntry.setEntityType("StablecoinTransaction");
            auditEntry.setEntityId(txId);
            auditEntry.setFromStatus(TransactionStatus.UNASSIGNED);
            auditEntry.setToStatus(TransactionStatus.SETTLED);
            auditEntry.setAction("MANUAL_REASSIGN");
            auditEntry.setUserId("BANK_ADMIN");
            auditEntry.setDetails("Manuelle Zuordnung: Ziel-Konto " + targetCustomerId +
                    " (IBAN: " + targetIban + "), Betrag: " + amountFiat + " " + currency);
            auditLogRepository.save(auditEntry);

            // TX frisch laden für Response
            StablecoinTransaction settledTx = txRepository.findById(txId)
                    .orElseThrow(() -> new NoSuchElementException("TX nach Update nicht gefunden: " + txId));

            log.info("[REASSIGN] TX {} erfolgreich auf Konto {} (Tenant {}) umgebucht und SETTLED",
                    txId, targetCustomerId, targetTenantId);

            return new TransactionResponse(
                    settledTx.getId(), settledTx.getType(), settledTx.getStatus(),
                    settledTx.getAmountFiat(), settledTx.getAmountStablecoin(),
                    settledTx.getCurrency(), settledTx.getBlockchainHash(),
                    settledTx.getGrossRevenue(), false,
                    settledTx.getCreatedAt(), settledTx.getSettledAt(), List.of());
        } finally {
            TenantContext.clear();
        }
    }
}
