package de.atruvia.stablecoin.kafka.dev;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.atruvia.stablecoin.kafka.KafkaTopics;
import de.atruvia.stablecoin.kafka.event.ComplianceScreeningEvent;
import de.atruvia.stablecoin.kafka.event.TransferStatusEvent;
import de.atruvia.stablecoin.kafka.event.YieldLifecycleEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Dev-Profil: Simulierter Kafka-Consumer über Spring @EventListener.
 *
 * Empfängt KafkaSimulationEvents, die vom DevKafkaProducer veröffentlicht werden,
 * und verarbeitet sie topic-spezifisch — wie ein echter Kafka-Consumer-Group-Member.
 *
 * Consumer-Groups (simuliert):
 *   - ledger-service:       stablecoin-transfers → Hauptbuch-Buchungen
 *   - notification-service: stablecoin-transfers → E-Mail/Push
 *   - compliance-processor: compliance-screening → Chainalysis-Ergebnis verarbeiten
 *   - analytics-sink:       stablecoin-analytics-v1 → Lakehouse-Schreiben
 */
@Component
@Profile("dev")
public class DevKafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(DevKafkaConsumer.class);
    private final ObjectMapper objectMapper;

    public DevKafkaConsumer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Consumer-Group: ledger-service
     * Verarbeitet Statuswechsel auf stablecoin-transfers → simuliert Hauptbuch-Buchung.
     */
    @EventListener
    @Async
    public void onTransferStatus(KafkaSimulationEvent event) {
        if (!KafkaTopics.STABLECOIN_TRANSFERS.equals(event.getTopic())) return;
        try {
            TransferStatusEvent tx = objectMapper.readValue(event.getJsonPayload(), TransferStatusEvent.class);
            log.info("[DEV-KAFKA-CONSUMER] [ledger-service] ← stablecoin-transfers | " +
                     "tx={} {} → {} amount={}EUR tenant={}",
                     tx.transactionId(), tx.previousStatus(), tx.currentStatus(),
                     tx.amountFiat(), tx.tenantId());

            if ("SETTLED".equals(tx.currentStatus())) {
                log.info("[DEV-KAFKA-CONSUMER] [ledger-service] ✓ Hauptbuch-Buchung simuliert: " +
                         "SETTLED tx={} revenue={}EUR", tx.transactionId(), tx.grossRevenue());
            }
            if ("FAILED".equals(tx.currentStatus())) {
                log.warn("[DEV-KAFKA-CONSUMER] [ledger-service] ✗ Rollback-Buchung simuliert: FAILED tx={}",
                         tx.transactionId());
            }
        } catch (Exception e) {
            log.error("[DEV-KAFKA-CONSUMER] Deserialisierungsfehler stablecoin-transfers: {}", e.getMessage());
        }
    }

    /**
     * Consumer-Group: compliance-processor
     * Empfängt Screening-Aufträge und gibt Ergebnis zurück (simuliert Chainalysis-Roundtrip).
     */
    @EventListener
    @Async
    public void onComplianceScreening(KafkaSimulationEvent event) {
        if (!KafkaTopics.COMPLIANCE_SCREENING.equals(event.getTopic())) return;
        if (!"SCREENING_REQUESTED".equals(event.getEventType())) return;
        try {
            ComplianceScreeningEvent req = objectMapper.readValue(event.getJsonPayload(), ComplianceScreeningEvent.class);
            log.info("[DEV-KAFKA-CONSUMER] [compliance-processor] ← compliance-screening | " +
                     "wallet={} direction={} correlationId={}",
                     req.walletAddress(), req.direction(), req.correlationId());
            // Simulated result: APPROVED (real consumer would call Chainalysis API)
            log.info("[DEV-KAFKA-CONSUMER] [compliance-processor] → Result: APPROVED (mock) wallet={}",
                     req.walletAddress());
        } catch (Exception e) {
            log.error("[DEV-KAFKA-CONSUMER] Deserialisierungsfehler compliance-screening: {}", e.getMessage());
        }
    }

    /**
     * Consumer-Group: analytics-sink
     * Empfängt Analytics-Events und simuliert Lakehouse-Schreiben.
     */
    @EventListener
    @Async
    public void onAnalytics(KafkaSimulationEvent event) {
        if (!KafkaTopics.ANALYTICS_V1.equals(event.getTopic())) return;
        log.info("[DEV-KAFKA-CONSUMER] [analytics-sink] ← stablecoin-analytics-v1 | " +
                 "type={} tenant={} → Lakehouse simuliert",
                 event.getEventType(), extractTenantId(event.getJsonPayload()));
    }

    /**
     * Consumer-Group: yield-monitor
     * Empfängt Yield-Ereignisse für BaFin-Meldewesen (simuliert).
     */
    @EventListener
    @Async
    public void onYieldLifecycle(KafkaSimulationEvent event) {
        if (!KafkaTopics.YIELD_LIFECYCLE.equals(event.getTopic())) return;
        try {
            YieldLifecycleEvent yl = objectMapper.readValue(event.getJsonPayload(), YieldLifecycleEvent.class);
            log.info("[DEV-KAFKA-CONSUMER] [yield-monitor] ← yield-lifecycle | {} position={} customer={} principal={}EUR",
                     yl.eventType(), yl.positionId(), yl.customerId(), yl.principalEur());
        } catch (Exception e) {
            log.error("[DEV-KAFKA-CONSUMER] Deserialisierungsfehler yield-lifecycle: {}", e.getMessage());
        }
    }

    private String extractTenantId(String json) {
        try {
            return objectMapper.readTree(json).path("tenantId").asText("unknown");
        } catch (Exception e) {
            return "unknown";
        }
    }
}
