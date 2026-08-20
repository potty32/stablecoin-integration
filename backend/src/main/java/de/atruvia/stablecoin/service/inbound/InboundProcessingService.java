package de.atruvia.stablecoin.service.inbound;

import de.atruvia.stablecoin.client.CoreBankingClient;
import de.atruvia.stablecoin.client.TokenAdapterRouter;
import de.atruvia.stablecoin.client.dto.AdapterTransferRequest;
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
    private final TokenAdapterRouter tokenAdapterRouter;

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
            CoreBankingClient coreBankingClient,
            TokenAdapterRouter tokenAdapterRouter) {
        this.adminJdbcTemplate = adminJdbcTemplate;
        this.accountRepository = accountRepository;
        this.txRepository = txRepository;
        this.auditLogRepository = auditLogRepository;
        this.outboxRepository = outboxRepository;
        this.complianceService = complianceService;
        this.fxRateService = fxRateService;
        this.coreBankingClient = coreBankingClient;
        this.tokenAdapterRouter = tokenAdapterRouter;
    }

    /**
     * Verarbeitet einen eingehenden Circle/Taurus-Webhook.
     * Kein eigenes @Transactional — delegiert an REQUIRES_NEW-Methoden für atomare Commits.
     */
    public TransactionResponse processInbound(InboundWebhookRequest request) {
        log.info("[INBOUND] Webhook empfangen: walletId={} amount={} {} hash={}",
                request.walletId(), request.amount(), request.currency(), request.blockchainHash());

        // 1. Cross-Tenant-Lookup: adminJdbcTemplate (BYPASSRLS) → kein TenantContext nötig
        Map<String, Object> accountRow = findAccountRowByWalletAddressOrNull(request.walletId());

        // UC-31: Sammelkonto — unbekannte Wallet → Transaktion nicht verwerfen
        if (accountRow == null) {
            log.warn("[INBOUND] Wallet {} keinem Kundenkonto zuordenbar — Buchung auf Sammelkonto",
                    request.walletId());
            return self.processUnassignedInbound(request);
        }

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

            // UC-30: Konto-Status-Prüfung vor Gutschrift
            // Sender ist sauber — prüfe ob Empfänger-Konto aktiv ist
            CustomerAccount recipientAccount = accountRepository.findByIban(recipientIban)
                    .orElseThrow(() -> new NoSuchElementException("Konto nicht gefunden: " + recipientIban));

            if (recipientAccount.getStatus() != AccountStatus.ACTIVE) {
                log.warn("[INBOUND] Konto {} ist {} — automatische Retoure zu Sender {}",
                        recipientAccount.getCustomerId(), recipientAccount.getStatus(), senderWallet);
                self.initiateInboundReturn(txId, recipientAccount, senderWallet, currency, amountFiat);
                return;
            }

            // LOW/MEDIUM-Risk + aktives Konto: FX-Konvertierung + Core-Banking-Gutschrift
            // FX-Semantik: getBaseRate(USDC) liefert EUR/USD (Preis von 1 EUR in USDC, z.B. 1.0823).
            // Inbound: X USDC → EUR = X / rate  (nicht ×, sonst 17 % Überkreditierung)
            BigDecimal baseRate = fxRateService.getBaseRate(currency);
            BigDecimal amountEur = amountFiat.divide(baseRate, 6, RoundingMode.HALF_UP);

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

    // ── UC-30: Automatische Retoure bei inaktivem Konto ─────────────────────

    /**
     * Erstellt eine INBOUND_RETURN-Transaktion und sendet den Betrag via Circle
     * an die originale Absender-Wallet zurück.
     *
     * Original-TX: COMPLIANCE_APPROVED → FAILED (Konto nicht aktiv)
     * Return-TX:   CREATED → RETURNED (Circuit-Transfer abgeschlossen)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void initiateInboundReturn(UUID originalTxId, CustomerAccount account,
                                      String senderWallet, StablecoinCurrency currency,
                                      BigDecimal amount) {
        log.info("[INBOUND-RETURN] Retoure für tx={} senderWallet={} amount={} {}",
                originalTxId, senderWallet, amount, currency);

        // Original-TX auf FAILED setzen
        transferService.transitionToFailed(originalTxId,
                "KONTO_INAKTIV: Retoure an " + senderWallet + " initiiert", "SYSTEM");

        // INBOUND_RETURN-TX anlegen
        StablecoinTransaction returnTx = new StablecoinTransaction();
        returnTx.setCustomerAccount(account);
        returnTx.setIdempotencyKey("return:" + originalTxId);
        returnTx.setType(TransactionType.INBOUND_RETURN);
        returnTx.setCurrency(currency);
        returnTx.setAmountFiat(amount);
        returnTx.setAmountStablecoin(amount);
        returnTx.setFxRate(BigDecimal.ONE);
        returnTx.setFxSpread(BigDecimal.ZERO);
        returnTx.setTransactionFee(BigDecimal.ZERO);
        returnTx.setGrossRevenue(BigDecimal.ZERO);
        returnTx.setSourceWallet("BANK_MASTER_WALLET_ID");
        returnTx.setDestinationWallet(senderWallet);
        returnTx.setParentTransactionId(originalTxId);
        returnTx.setStatus(CREATED);
        StablecoinTransaction savedReturn = txRepository.save(returnTx);

        // Token-Adapter: Betrag zurück an Absender
        tokenAdapterRouter.getAdapter(currency)
                .initiateReturn(new AdapterTransferRequest(
                        "return-" + originalTxId,
                        "BANK_MASTER_WALLET_ID",
                        senderWallet,
                        amount,
                        currency));

        // Return-TX direkt in dieser T3-Transaktion auf RETURNED setzen.
        // transitionTo(REQUIRED_NEW) kann savedReturn nicht sehen, da T3 noch nicht committed ist —
        // daher Status direkt setzen statt über den State-Machine-Proxy.
        savedReturn.setStatus(RETURNED);
        txRepository.save(savedReturn);

        saveTransitionLog(savedReturn.getId(), CREATED, RETURNED, "SYSTEM",
                "Retoure abgeschlossen: Betrag=" + amount + " " + currency
                        + " → " + senderWallet + " (Ursprungs-TX: " + originalTxId + ")");

        log.info("[INBOUND-RETURN] Retoure abgeschlossen: returnTx={} originalTx={}",
                savedReturn.getId(), originalTxId);
    }

    // ── UC-31: Sammelkonto für nicht zuordenbare Geldeingänge ───────────────

    /**
     * Bucht einen Stablecoin-Eingang, dessen Wallet-Adresse keinem Kundenkonto zugeordnet
     * werden kann, auf das Sammelkonto (customer_id='unassigned-funds').
     * Status bleibt UNASSIGNED bis ein Sachbearbeiter die manuelle Zuordnung vornimmt.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TransactionResponse processUnassignedInbound(InboundWebhookRequest request) {
        // Sammelkonto liegt im 'tenant-default' Mandanten
        TenantContext.set("tenant-default");
        try {
            // Idempotenz-Check (auch für Sammelkonto)
            txRepository.findByBlockchainHash(request.blockchainHash()).ifPresent(existing -> {
                throw new IdempotencyConflictException(existing.getId());
            });

            CustomerAccount collectionAccount = accountRepository.findByCustomerId("unassigned-funds")
                    .orElseThrow(() -> new IllegalStateException(
                            "Sammelkonto 'unassigned-funds' nicht gefunden — V10-Migration prüfen"));

            StablecoinCurrency currency = StablecoinCurrency.valueOf(request.currency().toUpperCase());

            StablecoinTransaction tx = new StablecoinTransaction();
            tx.setCustomerAccount(collectionAccount);
            tx.setIdempotencyKey("unassigned:" + request.blockchainHash());
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
            tx.setStatus(UNASSIGNED);
            StablecoinTransaction saved = txRepository.save(tx);

            AuditLog logEntry = new AuditLog();
            logEntry.setTransactionId(saved.getId());
            logEntry.setEntityType("StablecoinTransaction");
            logEntry.setEntityId(saved.getId());
            logEntry.setAction("UNASSIGNED_INBOUND");
            logEntry.setUserId("SYSTEM");
            logEntry.setDetails("Nicht zuordenbare Wallet " + request.walletId()
                    + " — Betrag=" + request.amount() + " " + currency + " auf Sammelkonto gebucht");
            auditLogRepository.save(logEntry);

            log.info("[UNASSIGNED] TX {} auf Sammelkonto gebucht: wallet={} amount={} {}",
                    saved.getId(), request.walletId(), request.amount(), currency);
            return buildResponse(saved);
        } finally {
            TenantContext.clear();
        }
    }

    private Map<String, Object> findAccountRowByWalletAddressOrNull(String walletAddress) {
        List<Map<String, Object>> rows = adminJdbcTemplate.queryForList(
                "SELECT id, tenant_id FROM customer_account WHERE wallet_address = ?", walletAddress);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private Map<String, Object> findAccountRowByWalletAddress(String walletAddress) {
        Map<String, Object> row = findAccountRowByWalletAddressOrNull(walletAddress);
        if (row == null) throw new NoSuchElementException("Kein Kundenkonto für walletId: " + walletAddress);
        return row;
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
