package de.atruvia.stablecoin.service.inbound;

import de.atruvia.stablecoin.client.CoreBankingClient;
import de.atruvia.stablecoin.client.dto.LedgerBookingDto;
import de.atruvia.stablecoin.config.TenantContext;
import de.atruvia.stablecoin.dto.request.InboundWebhookRequest;
import de.atruvia.stablecoin.dto.response.TransactionResponse;
import de.atruvia.stablecoin.entity.*;
import de.atruvia.stablecoin.exception.ComplianceBlockException;
import de.atruvia.stablecoin.exception.IdempotencyConflictException;
import de.atruvia.stablecoin.repository.*;
import de.atruvia.stablecoin.service.b2b.B2bTransferService;
import de.atruvia.stablecoin.service.compliance.ComplianceService;
import de.atruvia.stablecoin.service.fx.FxRateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

import static de.atruvia.stablecoin.entity.TransactionStatus.*;

/**
 * Orchestriert den Inbound-Stablecoin-Empfang:
 * 1. Cross-Tenant-Lookup der Empfänger-Wallet (adminJdbcTemplate, BYPASSRLS)
 * 2. Idempotenz-Check via blockchainHash
 * 3. TX-Persistierung (CREATED → INCOMING)
 * 4. Post-Receive AML-Screening (Chainalysis, "incoming")
 * 5. Bei Freigabe: FX-Konvertierung + CoreBanking-Gutschrift + SETTLED
 * 6. Bei Block: AuditLog AML_INBOUND_BLOCK + COMPLIANCE_REJECTED → FAILED
 */
@Service
public class InboundProcessingService {

    private static final Logger log = LoggerFactory.getLogger(InboundProcessingService.class);
    private static final String BANK_STABLECOIN_TRANSIT = "BANK_STABLECOIN_TRANSIT_ACCOUNT";

    private final JdbcTemplate adminJdbcTemplate;
    private final CustomerAccountRepository accountRepository;
    private final StablecoinTransactionRepository txRepository;
    private final AuditLogRepository auditLogRepository;
    private final OutboxMessageRepository outboxRepository;
    private final ComplianceService complianceService;
    private final FxRateService fxRateService;
    private final CoreBankingClient coreBankingClient;

    // REQUIRES_NEW-State-Machine aus B2bTransferService nutzen (shared State Machine)
    @Lazy @Autowired
    private B2bTransferService transferService;

    // Self-Injection für eigene REQUIRES_NEW-Methode
    @Lazy @Autowired
    private InboundProcessingService self;

    public InboundProcessingService(
            @Qualifier("adminJdbcTemplate") JdbcTemplate adminJdbcTemplate,
            CustomerAccountRepository accountRepository,
            StablecoinTransactionRepository txRepository,
            AuditLogRepository auditLogRepository,
            OutboxMessageRepository outboxRepository,
            ComplianceService complianceService,
            FxRateService fxRateService,
            CoreBankingClient coreBankingClient) {
        this.adminJdbcTemplate = adminJdbcTemplate;
        this.accountRepository = accountRepository;
        this.txRepository = txRepository;
        this.auditLogRepository = auditLogRepository;
        this.outboxRepository = outboxRepository;
        this.complianceService = complianceService;
        this.fxRateService = fxRateService;
        this.coreBankingClient = coreBankingClient;
    }

    /**
     * Verarbeitet einen eingehenden Circle/Taurus-Webhook.
     * Kein eigenes @Transactional — delegiert an REQUIRES_NEW-Methoden für atomare Commits.
     */
    public TransactionResponse processInbound(InboundWebhookRequest request) {
        log.info("[INBOUND] Webhook empfangen: walletId={} amount={} {} hash={}",
                request.walletId(), request.amount(), request.currency(), request.blockchainHash());

        // 1. Cross-Tenant-Lookup: adminJdbcTemplate (BYPASSRLS) → kein TenantContext nötig
        Map<String, Object> accountRow = findAccountRowByWalletAddress(request.walletId());
        String tenantId  = (String) accountRow.get("tenant_id");
        UUID   accountId = (UUID) accountRow.get("id");

        // 2. Tenant-Kontext setzen — alle weiteren DB-Ops laufen jetzt mit korrektem Tenant
        TenantContext.set(tenantId);
        try {
            // 3. Vollständiges Account-Objekt laden (mit RLS — jetzt korrekt gesetzt)
            CustomerAccount account = accountRepository.findById(accountId)
                    .orElseThrow(() -> new NoSuchElementException("Account nicht gefunden: " + accountId));

            // 4. Idempotenz: blockchainHash = natürlicher Schlüssel für On-Chain-Transaktionen
            txRepository.findByBlockchainHash(request.blockchainHash()).ifPresent(existing -> {
                log.info("[INBOUND] Duplikat erkannt — blockchainHash={} tx={}", request.blockchainHash(), existing.getId());
                throw new IdempotencyConflictException(existing.getId());
            });

            // 5. TX atomisch persistieren (CREATED → INCOMING + OutboxMsg)
            StablecoinTransaction tx = self.persistInitialInboundTransaction(request, account);

            // 6. Compliance-Flow: Screening + Gutschrift oder Blockierung
            executeInboundComplianceFlow(tx.getId(), request.senderWallet(),
                    tx.getCurrency(), tx.getAmountFiat(), account.getIban());

            // Aktuelle TX-Version laden für die Response
            StablecoinTransaction finalTx = txRepository.findById(tx.getId())
                    .orElseThrow(() -> new NoSuchElementException("TX verschwunden: " + tx.getId()));
            log.info("[INBOUND] Verarbeitung abgeschlossen: tx={} status={}", tx.getId(), finalTx.getStatus());
            return buildResponse(finalTx);

        } finally {
            TenantContext.clear();
        }
    }

    /**
     * Persistiert die initiale Inbound-TX und setzt sie auf INCOMING.
     * REQUIRES_NEW = atomar: TX-Insert + OutboxMsg in einem Commit.
     * Bei Crash nach diesem Commit startet der OutboxProcessor die Recovery.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public StablecoinTransaction persistInitialInboundTransaction(
            InboundWebhookRequest request, CustomerAccount account) {

        StablecoinCurrency currency = StablecoinCurrency.valueOf(request.currency().toUpperCase());

        StablecoinTransaction tx = new StablecoinTransaction();
        tx.setCustomerAccount(account);
        tx.setIdempotencyKey("inbound:" + request.blockchainHash());
        tx.setType(TransactionType.INBOUND);
        tx.setCurrency(currency);
        tx.setAmountFiat(request.amount());
        tx.setAmountStablecoin(request.amount());
        tx.setFxRate(BigDecimal.ONE);
        tx.setFxSpread(BigDecimal.ZERO);
        tx.setTransactionFee(BigDecimal.ZERO);
        tx.setGrossRevenue(BigDecimal.ZERO);
        tx.setSourceWallet(request.senderWallet());
        tx.setDestinationWallet(request.walletId());
        tx.setBlockchainHash(request.blockchainHash());
        tx.setStatus(CREATED);
        // tenantId wird von TenantEntityListener (@PrePersist) aus TenantContext gesetzt
        StablecoinTransaction saved = txRepository.save(tx);

        // CREATED → INCOMING (innerhalb dieser REQUIRES_NEW-TX)
        saved.setStatus(INCOMING);
        txRepository.save(saved);
        saveTransitionLog(saved.getId(), CREATED, INCOMING, "CIRCLE_WEBHOOK",
                "Blockchain-Eingang registriert: hash=" + request.blockchainHash());

        // Outbox-Recovery-Marker: Wenn System hier crasht, startet OutboxProcessor die Compliance neu
        saveOutboxMessage(saved.getId(), "PROCESS_INBOUND_COMPLIANCE",
                String.format("{\"txId\":\"%s\"}", saved.getId()));

        log.info("[INBOUND] TX {} erstellt und auf INCOMING gesetzt", saved.getId());
        return saved;
    }

    /**
     * Post-Receive AML-Screening + Core-Banking-Gutschrift.
     * Öffentlich: wird auch vom OutboxProcessor für Crash-Recovery aufgerufen.
     */
    public void executeInboundComplianceFlow(UUID txId, String senderWallet,
                                              StablecoinCurrency currency, BigDecimal amountFiat,
                                              String recipientIban) {
        try {
            transferService.transitionTo(txId, COMPLIANCE_PENDING, "SYSTEM");

            // AML-Screening (Chainalysis) — direction="incoming" für den Sender
            complianceService.screenAndAssert(senderWallet, txId, "SYSTEM", "incoming");

            // LOW/MEDIUM-Risk: FX-Konvertierung + Core-Banking-Gutschrift
            BigDecimal baseRate = fxRateService.getBaseRate(currency);
            BigDecimal amountEur = amountFiat.multiply(baseRate).setScale(6, RoundingMode.HALF_UP);

            coreBankingClient.createLedgerBooking(new LedgerBookingDto(
                    txId.toString(),
                    BANK_STABLECOIN_TRANSIT,
                    List.of(new LedgerBookingDto.CreditEntry(recipientIban, amountEur, "INBOUND_CREDIT")),
                    amountEur, "EUR", LocalDate.now()
            ));
            log.info("[INBOUND] Core-Banking-Gutschrift: tx={} iban={} amountEur={}", txId, recipientIban, amountEur);

            // TX auf COMPLIANCE_APPROVED + fxRate + amountFiat (EUR) aktualisieren (via self.* für REQUIRES_NEW-Proxy)
            self.updateTxWithFxAndSettle(txId, baseRate, amountEur);
            transferService.transitionTo(txId, COMPLIANCE_APPROVED, "SYSTEM");
            transferService.transitionTo(txId, SETTLED, "SYSTEM");

            log.info("[INBOUND] TX {} erfolgreich auf SETTLED gesetzt", txId);

        } catch (ComplianceBlockException e) {
            log.warn("[INBOUND] AML-Block tx={} sender={} reason={}", txId, senderWallet, e.getMessage());

            // AML_INBOUND_BLOCK AuditLog-Eintrag (BaFin-relevantes Ereignis)
            AuditLog blockEntry = new AuditLog();
            blockEntry.setTransactionId(txId);
            blockEntry.setEntityType("StablecoinTransaction");
            blockEntry.setEntityId(txId);
            blockEntry.setAction("AML_INBOUND_BLOCK");
            blockEntry.setUserId("SYSTEM");
            blockEntry.setDetails("Inbound AML-Block: senderWallet=" + senderWallet
                    + ", reason=" + e.getMessage());
            auditLogRepository.save(blockEntry);

            transferService.transitionTo(txId, COMPLIANCE_REJECTED, "SYSTEM");
            transferService.transitionToFailed(txId, "AML_INBOUND_BLOCK: " + e.getMessage(), "SYSTEM");
        }
    }

    // ── Private Helpers ──────────────────────────────────────────────────────

    private Map<String, Object> findAccountRowByWalletAddress(String walletAddress) {
        List<Map<String, Object>> rows = adminJdbcTemplate.queryForList(
                "SELECT id, tenant_id FROM customer_account WHERE wallet_address = ?", walletAddress);
        if (rows.isEmpty()) {
            throw new NoSuchElementException("Keine Kundenkonto für walletId: " + walletAddress);
        }
        return rows.get(0);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateTxWithFxAndSettle(UUID txId, BigDecimal fxRate, BigDecimal amountEur) {
        StablecoinTransaction tx = txRepository.findByIdWithLock(txId)
                .orElseThrow(() -> new NoSuchElementException("TX not found: " + txId));
        tx.setFxRate(fxRate);
        tx.setAmountFiat(amountEur);
        tx.setGrossRevenue(BigDecimal.ZERO);
        tx.setSettledAt(LocalDateTime.now());
        txRepository.save(tx);
    }

    private void saveOutboxMessage(UUID transactionId, String eventType, String payload) {
        OutboxMessage msg = new OutboxMessage();
        msg.setTransactionId(transactionId);
        msg.setAggregateType("StablecoinTransaction");
        msg.setEventType(eventType);
        msg.setPayload(payload);
        outboxRepository.save(msg);
    }

    private void saveTransitionLog(UUID txId, TransactionStatus from, TransactionStatus to,
                                   String userId, String details) {
        AuditLog entry = new AuditLog();
        entry.setTransactionId(txId);
        entry.setEntityType("StablecoinTransaction");
        entry.setEntityId(txId);
        entry.setFromStatus(from);
        entry.setToStatus(to);
        entry.setAction("STATUS_CHANGE");
        entry.setUserId(userId);
        entry.setDetails(details);
        auditLogRepository.save(entry);
    }

    private TransactionResponse buildResponse(StablecoinTransaction tx) {
        return new TransactionResponse(
                tx.getId(), tx.getType(), tx.getStatus(),
                tx.getAmountFiat(), tx.getAmountStablecoin(), tx.getCurrency(),
                tx.getBlockchainHash(), tx.getGrossRevenue(), false,
                tx.getCreatedAt(), tx.getSettledAt(), List.of()
        );
    }
}
