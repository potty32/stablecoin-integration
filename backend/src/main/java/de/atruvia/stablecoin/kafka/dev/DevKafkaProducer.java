package de.atruvia.stablecoin.kafka.dev;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import de.atruvia.stablecoin.kafka.KafkaEventProducer;
import de.atruvia.stablecoin.kafka.event.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * Dev-Profil: Kafka-Simulation über Spring ApplicationEventPublisher.
 *
 * Jede publish()-Methode:
 *   1. Serialisiert das Event als JSON (ObjectMapper, kein Avro)
 *   2. Loggt: "[DEV-KAFKA] → topic: {json}"
 *   3. Publiziert ein KafkaSimulationEvent über Spring ApplicationEventPublisher
 *   4. Speichert das Event im InMemoryEventStore (für Dev-Inspektion)
 *
 * Im Prod-Profil: durch KafkaTemplateEventProducer ersetzen (spring-kafka dependency ergänzen).
 */
@Service
@Profile("dev")
public class DevKafkaProducer implements KafkaEventProducer {

    private static final Logger log = LoggerFactory.getLogger(DevKafkaProducer.class);

    private final ApplicationEventPublisher publisher;
    private final InMemoryEventStore eventStore;
    private final ObjectMapper objectMapper;

    public DevKafkaProducer(ApplicationEventPublisher publisher,
                             InMemoryEventStore eventStore,
                             ObjectMapper objectMapper) {
        this.publisher = publisher;
        this.eventStore = eventStore;
        // Jackson mit JavaTimeModule für Instant-Serialisierung
        this.objectMapper = objectMapper.copy().registerModule(new JavaTimeModule());
    }

    @Override
    public void publishTransferStatus(TransferStatusEvent event) {
        publish(event.topic(), event.eventType(), event);
    }

    @Override
    public void publishComplianceRequest(ComplianceScreeningEvent event) {
        publish(event.topic(), event.eventType(), event);
    }

    @Override
    public void publishComplianceResult(ComplianceScreeningEvent event) {
        publish(event.topic(), event.eventType(), event);
    }

    @Override
    public void publishYieldLifecycle(YieldLifecycleEvent event) {
        publish(event.topic(), event.eventType(), event);
    }

    @Override
    public void publishAnalytics(AnalyticsEvent event) {
        publish(event.topic(), event.eventType(), event);
    }

    // ── Private ──────────────────────────────────────────────────────────────────

    private void publish(String topic, String eventType, Object payload) {
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.error("[DEV-KAFKA] Serialisierungsfehler topic={} type={}: {}", topic, eventType, e.getMessage());
            return;
        }

        log.info("[DEV-KAFKA] → {} | {}: {}", topic, eventType, json);
        eventStore.add(topic, eventType, json);
        publisher.publishEvent(new KafkaSimulationEvent(this, topic, eventType, json, payload));
    }
}
