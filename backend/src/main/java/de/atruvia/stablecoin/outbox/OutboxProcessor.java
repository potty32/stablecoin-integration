package de.atruvia.stablecoin.outbox;

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
import de.atruvia.stablecoin.service.revenue.RevenueService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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

    // @Lazy verhindert zirkuläre Abhängigkeit OutboxProcessor ↔ B2bTransferService
    @Lazy @Autowired
    private B2bTransferService transferService;

    public OutboxProcessor(
            OutboxMessageRepository outboxRepository,
            N8nWebhookClient n8nWebhookClient,
            StablecoinTransactionRepository txRepository,
            CircleWalletClient circleWalletClient,
            RevenueService revenueService) {
        this.outboxRepository = outboxRepository;
        this.n8nWebhookClient = n8nWebhookClient;
        this.txRepository = txRepository;
        this.circleWalletClient = circleWalletClient;
        this.revenueService = revenueService;
    }

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void processPendingMessages() {
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
            case "SUBMIT_TO_BLOCKCHAIN" -> recoverSubmitToBlockchain(msg);
            case "TRANSACTION_SETTLED"  -> log.info("[OUTBOX] Settlement event recorded txId={}", msg.getTransactionId());
            case "TRANSACTION_INITIATED"-> log.info("[OUTBOX] Initiation event recorded txId={}", msg.getTransactionId());
            case "TRANSACTION_FAILED"   -> log.warn("[OUTBOX] Failed event recorded txId={}", msg.getTransactionId());
            default -> log.debug("[OUTBOX] Unhandled event type={} txId={}", msg.getEventType(), msg.getTransactionId());
        }
    }

    /**
     * Crash-Recovery für den FUNDS_HELD/SUBMITTED-Bereich.
     * Wird bei jedem Neustart und alle 5 Sekunden ausgeführt bis die TX finalisiert ist.
     *
     * Mögliche TX-Zustände beim Neustart:
     * - FUNDS_HELD: Circle wurde noch nicht aufgerufen → Alert (manueller Eingriff nötig)
     * - SUBMITTED + circleTransactionId: Circle aufgerufen, aber Status noch unklar → pollen
     * - SETTLED/FAILED/REDEEMED/REJECTED/EXPIRED: bereits finalisiert → Outbox als SENT markieren
     */
    private void recoverSubmitToBlockchain(OutboxMessage msg) {
        UUID txId = msg.getTransactionId();
        StablecoinTransaction tx = txRepository.findById(txId).orElse(null);

        if (tx == null) {
            log.warn("[RECOVERY] TX {} nicht in DB gefunden — Outbox-Nachricht wird als SENT markiert", txId);
            return;
        }

        TransactionStatus status = tx.getStatus();
        log.info("[RECOVERY] Prüfe TX={} status={}", txId, status);

        switch (status) {
            case SETTLED, FAILED, REJECTED, EXPIRED, REDEEMED -> {
                // TX bereits finalisiert — Recovery nicht nötig
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
                // Hier könnte in Prod ein PagerDuty-Alert ausgelöst werden
                // TX bleibt in FUNDS_HELD — Hold ist gesperrt, Geld wurde NICHT übertragen
                throw new IllegalStateException("TX stuck in FUNDS_HELD: " + txId);
            }
            default -> log.warn("[RECOVERY] Unerwarteter Status {} für TX={}", status, txId);
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
                // PENDING oder anderer Zustand: nächste Iteration abwarten
                log.info("[RECOVERY] TX={} Circle-Status={} — nächste Runde in 5s", txId, circleStatus.status());
                throw new IllegalStateException("Circle noch nicht fertig: " + circleStatus.status());
            }
        }
    }
}
