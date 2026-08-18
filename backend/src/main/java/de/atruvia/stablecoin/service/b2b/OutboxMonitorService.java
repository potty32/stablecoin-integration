package de.atruvia.stablecoin.service.b2b;

import de.atruvia.stablecoin.client.N8nWebhookClient;
import de.atruvia.stablecoin.entity.OutboxMessage;
import de.atruvia.stablecoin.entity.OutboxStatus;
import de.atruvia.stablecoin.repository.OutboxMessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * G-11: Outbox-Monitor — Alerting bei steckengebliebenen PENDING-Nachrichten.
 *
 * Prüft alle {@code app.outbox.alert-threshold-minutes} Minuten, ob Nachrichten
 * länger als die konfigurierte Schwelle in PENDING stecken. Bei Treffer: n8n-Alert.
 *
 * §25a KWG: Kreditinstitute müssen Zahlungsausfälle zeitnah erkennen und eskalieren.
 */
@Service
public class OutboxMonitorService {

    private static final Logger log = LoggerFactory.getLogger(OutboxMonitorService.class);

    @Value("${app.outbox.alert-threshold-minutes:15}")
    private int alertThresholdMinutes;

    private final OutboxMessageRepository outboxRepository;
    private final N8nWebhookClient n8nWebhookClient;

    public OutboxMonitorService(OutboxMessageRepository outboxRepository,
                                N8nWebhookClient n8nWebhookClient) {
        this.outboxRepository = outboxRepository;
        this.n8nWebhookClient = n8nWebhookClient;
    }

    @Scheduled(fixedDelayString = "${app.outbox.monitor-interval-ms:300000}")
    public void checkStuckMessages() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(alertThresholdMinutes);
        List<OutboxMessage> stuck = outboxRepository
                .findByStatusAndCreatedAtBefore(OutboxStatus.PENDING, threshold);

        if (stuck.isEmpty()) {
            return;
        }

        String oldestId = stuck.get(0).getId().toString();
        log.error("[OUTBOX-MONITOR] {} Nachricht(en) stecken seit >{} Minuten in PENDING. Älteste: {}",
                stuck.size(), alertThresholdMinutes, oldestId);

        try {
            n8nWebhookClient.notifyOutboxAlert(stuck.size(), alertThresholdMinutes, oldestId);
        } catch (Exception e) {
            log.warn("[OUTBOX-MONITOR] n8n-Alert fehlgeschlagen: {}", e.getMessage());
        }
    }
}
