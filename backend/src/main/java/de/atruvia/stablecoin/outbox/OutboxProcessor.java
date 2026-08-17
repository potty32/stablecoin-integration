package de.atruvia.stablecoin.outbox;

import de.atruvia.stablecoin.client.N8nWebhookClient;
import de.atruvia.stablecoin.entity.OutboxMessage;
import de.atruvia.stablecoin.entity.OutboxStatus;
import de.atruvia.stablecoin.repository.OutboxMessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class OutboxProcessor {

    private static final Logger log = LoggerFactory.getLogger(OutboxProcessor.class);
    private static final int BATCH_SIZE = 10;
    private static final int MAX_ATTEMPTS = 3;

    private final OutboxMessageRepository outboxRepository;
    private final N8nWebhookClient n8nWebhookClient;

    public OutboxProcessor(OutboxMessageRepository outboxRepository, N8nWebhookClient n8nWebhookClient) {
        this.outboxRepository = outboxRepository;
        this.n8nWebhookClient = n8nWebhookClient;
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
            case "TRANSACTION_SETTLED"   -> log.info("[OUTBOX] Settlement event recorded txId={}", msg.getTransactionId());
            case "TRANSACTION_INITIATED" -> log.info("[OUTBOX] Initiation event recorded txId={}", msg.getTransactionId());
            case "TRANSACTION_FAILED"    -> log.warn("[OUTBOX] Failed event recorded txId={}", msg.getTransactionId());
            default -> log.debug("[OUTBOX] Unhandled event type={} txId={}", msg.getEventType(), msg.getTransactionId());
        }
    }
}
