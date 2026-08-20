package de.atruvia.stablecoin.outbox;

import de.atruvia.stablecoin.config.TenantContext;
import de.atruvia.stablecoin.client.CircleWalletClient;
import de.atruvia.stablecoin.client.N8nWebhookClient;
import de.atruvia.stablecoin.client.dto.CircleTransactionStatusDto;
import de.atruvia.stablecoin.entity.CustomerType;
import de.atruvia.stablecoin.entity.OutboxMessage;
import de.atruvia.stablecoin.entity.OutboxStatus;
import de.atruvia.stablecoin.entity.StablecoinTransaction;
import de.atruvia.stablecoin.entity.TransactionStatus;
import de.atruvia.stablecoin.repository.OutboxMessageRepository;
import de.atruvia.stablecoin.repository.StablecoinTransactionRepository;
import de.atruvia.stablecoin.service.b2b.B2bTransferService;
import de.atruvia.stablecoin.service.b2b.KillSwitchService;
import de.atruvia.stablecoin.service.inbound.InboundProcessingService;
import de.atruvia.stablecoin.service.revenue.RevenueService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

@Component
public class OutboxProcessor {

    private static final Logger log = LoggerFactory.getLogger(OutboxProcessor.class);
    private static final int BATCH_SIZE = 10;
    private static final int MAX_ATTEMPTS = 5;

    private final OutboxMessageRepository outboxRepository;
    private final N8nWebhookClient n8nWebhookClient;
    private final StablecoinTransactionRepository txRepository;
    private final CircleWalletClient circleWalletClient;
    private final RevenueService revenueService;

    /**
     * adminJdbcTemplate (BYPASSRLS — stablecoin-User als Table-Owner) für Cross-Tenant-Lookups.
     * Der OutboxProcessor läuft ohne JWT-Kontext — adminJdbcTemplate liefert die tenant_id,
     * bevor TenantContext gesetzt wird, damit txRepository (RLS-User) korrekt filtert.
     */
    private final JdbcTemplate adminJdbcTemplate;
    private final KillSwitchService killSwitchService;

    @Lazy @Autowired
    private B2bTransferService transferService;

    @Lazy @Autowired
    private InboundProcessingService inboundProcessingService;

    public OutboxProcessor(
            OutboxMessageRepository outboxRepository,
            N8nWebhookClient n8nWebhookClient,
            StablecoinTransactionRepository txRepository,
            CircleWalletClient circleWalletClient,
            RevenueService revenueService,
            @Qualifier("adminJdbcTemplate") JdbcTemplate adminJdbcTemplate,
            KillSwitchService killSwitchService) {
        this.outboxRepository = outboxRepository;
        this.n8nWebhookClient = n8nWebhookClient;
        this.txRepository = txRepository;
        this.circleWalletClient = circleWalletClient;
        this.revenueService = revenueService;
        this.adminJdbcTemplate = adminJdbcTemplate;
        this.killSwitchService = killSwitchService;
    }

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void processPendingMessages() {
        // F-07-Fix: Kill-Switch stoppt auch den Outbox-Processor (HTTP-Filter reicht nicht)
        if (killSwitchService.isGlobalKillSwitchActive()) {
            log.debug("[OUTBOX] Kill-Switch aktiv — Outbox-Verarbeitung pausiert");
            return;
        }
        List<OutboxMessage> pending = outboxRepository.findByStatusOrderByCreatedAtAsc(
                OutboxStatus.PENDING, PageRequest.of(0, BATCH_SIZE)
        );

        if (!pending.isEmpty()) {
            log.debug("[OUTBOX] Processing {} pending messages", pending.size());
        }

        for (OutboxMessage msg : pending) {
            try {
                processMessage(msg);
                msg.setStatus(OutboxStatus.SENT);
                msg.setProcessedAt(LocalDateTime.now());
                log.info("[OUTBOX] Processed event={} txId={}", msg.getEventType(), msg.getTransactionId());
            } catch (Exception e) {
                msg.setAttempts(msg.getAttempts() + 1);
                if (msg.getAttempts() >= MAX_ATTEMPTS) {
                    msg.setStatus(OutboxStatus.FAILED);
                    log.error("[OUTBOX] Permanently failed event={} txId={} after {} attempts",
                            msg.getEventType(), msg.getTransactionId(), msg.getAttempts());
                } else {
                    log.warn("[OUTBOX] Retry {}/{} for event={} txId={}: {}",
                            msg.getAttempts(), MAX_ATTEMPTS, msg.getEventType(), msg.getTransactionId(), e.getMessage());
                }
            }
            outboxRepository.save(msg);
        }
    }

    private void processMessage(OutboxMessage msg) {
        switch (msg.getEventType()) {
            case "SUBMIT_TO_BLOCKCHAIN"       -> recoverSubmitToBlockchain(msg);
            case "PROCESS_INBOUND_COMPLIANCE" -> recoverInboundCompliance(msg);
            case "TRANSACTION_SETTLED"        -> log.info("[OUTBOX] Settlement event recorded txId={}", msg.getTransactionId());
            case "TRANSACTION_INITIATED"      -> log.info("[OUTBOX] Initiation event recorded txId={}", msg.getTransactionId());
            case "TRANSACTION_FAILED"         -> log.warn("[OUTBOX] Failed event recorded txId={}", msg.getTransactionId());
            default -> log.debug("[OUTBOX] Unhandled event type={} txId={}", msg.getEventType(), msg.getTransactionId());
        }
    }

    /**
     * Crash-Recovery für Inbound-Compliance-Flow.
     *
     * Problem: OutboxProcessor hat keinen JWT-Kontext → TenantContext ist leer →
     * RLS-Policy auf stablecoin_transaction filtert alle Rows → txRepository.findById() gibt null.
     *
     * Fix: tenant_id zuerst via adminJdbcTemplate (BYPASSRLS) ermitteln,
     * dann TenantContext setzen, danach txRepository (RLS-User) aufrufen.
     */
    private void recoverInboundCompliance(OutboxMessage msg) {
        UUID txId = msg.getTransactionId();

        // 1. Cross-Tenant-Lookup via BYPASSRLS — kein TenantContext nötig
        String tenantId = lookupTenantIdBypassRls(txId);
        if (tenantId == null) {
            log.warn("[INBOUND-RECOVERY] TX {} nicht in DB — Outbox als SENT markieren", txId);
            return;
        }

        // 2. TenantContext setzen, damit txRepository (RLS-User stablecoin_app) die TX sieht
        TenantContext.set(tenantId);
        try {
            StablecoinTransaction tx = txRepository.findById(txId).orElse(null);
            if (tx == null) {
                log.warn("[INBOUND-RECOVERY] TX {} nicht über RLS sichtbar (tenantId={})", txId, tenantId);
                return;
            }

            TransactionStatus status = tx.getStatus();
            log.info("[INBOUND-RECOVERY] TX={} tenantId={} status={}", txId, tenantId, status);

            // Terminal-Zustände: bereits verarbeitet
            if (EnumSet.of(TransactionStatus.SETTLED, TransactionStatus.FAILED,
                           TransactionStatus.COMPLIANCE_APPROVED, TransactionStatus.COMPLIANCE_REJECTED).contains(status)) {
                log.info("[INBOUND-RECOVERY] TX {} bereits in Endzustand {} — keine Recovery nötig", txId, status);
                return;
            }

            // INCOMING: Compliance-Flow neu starten (idempotent dank COMPLIANCE_PENDING als Zwischenstatus)
            if (status == TransactionStatus.INCOMING) {
                log.info("[INBOUND-RECOVERY] Starte Compliance-Flow für TX={}", txId);
                inboundProcessingService.executeInboundComplianceFlow(
                        txId,
                        tx.getSourceWallet(),
                        tx.getCurrency(),
                        tx.getAmountFiat(),
                        tx.getCustomerAccount().getIban());
                return;
            }

            log.warn("[INBOUND-RECOVERY] Unerwarteter Status {} für TX={} — manueller Eingriff prüfen", status, txId);

        } finally {
            TenantContext.clear();
        }
    }

    /**
     * Ermittelt tenant_id für eine TX unter Umgehung von RLS (adminJdbcTemplate = stablecoin-User, Table-Owner).
     *
     * @return tenant_id oder null wenn TX nicht existiert
     */
    String lookupTenantIdBypassRls(UUID txId) {
        List<String> results = adminJdbcTemplate.queryForList(
                "SELECT tenant_id FROM stablecoin_transaction WHERE id = ?",
                String.class, txId);
        return results.isEmpty() ? null : results.get(0);
    }

    /**
     * Crash-Recovery für den FUNDS_HELD/SUBMITTED-Bereich.
     *
     * Mögliche TX-Zustände beim Neustart:
     * - FUNDS_HELD: Circle wurde noch nicht aufgerufen → Alert (manueller Eingriff nötig)
     * - SUBMITTED + circleTransactionId: Circle aufgerufen, aber Status noch unklar → pollen
     * - SETTLED/FAILED/REDEEMED/REJECTED/EXPIRED: bereits finalisiert → Outbox als SENT markieren
     */
    private void recoverSubmitToBlockchain(OutboxMessage msg) {
        UUID txId = msg.getTransactionId();

        // T-01-Fix: TenantContext muss für die gesamte Recovery gesetzt bleiben.
        // Der Outbox-Scheduler hat keinen JWT-Kontext → TenantContext ist leer → RLS filtert alle Zeilen.
        // Lösung: tenant_id via adminJdbcTemplate (BYPASSRLS = stablecoin Table-Owner) ermitteln,
        // dann TenantContext für alle nachfolgenden Repository- und Service-Aufrufe (REQUIRES_NEW) setzen.
        String tenantId = lookupTenantIdBypassRls(txId);
        if (tenantId == null) {
            log.warn("[RECOVERY] TX {} nicht via BYPASSRLS gefunden — Outbox-Nachricht wird als SENT markiert", txId);
            return;
        }
        TenantContext.set(tenantId);
        try {
            StablecoinTransaction tx = txRepository.findById(txId).orElse(null);

            if (tx == null) {
                log.warn("[RECOVERY] TX {} nicht in DB gefunden (tenantId={}) — Outbox-Nachricht wird als SENT markiert",
                        txId, tenantId);
                return;
            }

            TransactionStatus status = tx.getStatus();
            log.info("[RECOVERY] Prüfe TX={} status={} tenantId={}", txId, status, tenantId);

            switch (status) {
                case SETTLED, FAILED, REJECTED, EXPIRED, REDEEMED -> {
                    log.info("[RECOVERY] TX {} bereits in Endzustand {} — keine Recovery nötig", txId, status);
                }
                case SUBMITTED -> {
                    if (tx.getCircleTransactionId() != null) {
                        recoverFromCircle(tx);
                    } else {
                        log.error("[RECOVERY] TX {} ist SUBMITTED aber hat keine circleTransactionId — manueller Eingriff erforderlich!", txId);
                        throw new IllegalStateException("SUBMITTED ohne circleTransactionId: " + txId);
                    }
                }
                case FUNDS_HELD -> {
                    log.error("[RECOVERY] TX {} steckt in FUNDS_HELD — Circle wurde noch nicht aufgerufen. " +
                            "Hold={} ist aktiv. Manueller Eingriff erforderlich!", txId, tx.getHoldId());
                    throw new IllegalStateException("TX stuck in FUNDS_HELD: " + txId);
                }
                default -> log.warn("[RECOVERY] Unerwarteter Status {} für TX={}", status, txId);
            }
        } finally {
            TenantContext.clear();
        }
    }

    private void recoverFromCircle(StablecoinTransaction tx) {
        UUID txId = tx.getId();
        String circleId = tx.getCircleTransactionId();

        log.info("[RECOVERY] Polling Circle für TX={} circleId={}", txId, circleId);

        CircleTransactionStatusDto circleStatus = circleWalletClient.getTransactionStatus(circleId);
        log.info("[RECOVERY] Circle Status für TX={}: {}", txId, circleStatus.status());

        switch (circleStatus.status()) {
            case "COMPLETE" -> {
                RevenueService.RevenueCalculation revenue = revenueService.calculate(
                        tx.getAmountFiat(), CustomerType.B2B);
                transferService.settleTransaction(txId, circleStatus.transactionHash(), revenue);
                log.info("[RECOVERY] TX={} erfolgreich via Outbox-Recovery auf SETTLED gesetzt", txId);
            }
            case "FAILED" -> {
                transferService.transitionToFailed(txId, "CIRCLE_FAILED_ON_CHAIN (Recovery)", "SYSTEM");
                log.warn("[RECOVERY] TX={} via Outbox-Recovery auf FAILED gesetzt, Hold freigegeben", txId);
            }
            default -> {
                log.info("[RECOVERY] TX={} Circle-Status={} — nächste Runde in 5s", txId, circleStatus.status());
                throw new IllegalStateException("Circle noch nicht fertig: " + circleStatus.status());
            }
        }
    }
}
