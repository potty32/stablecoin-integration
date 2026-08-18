package de.atruvia.stablecoin.kafka;

import de.atruvia.stablecoin.kafka.event.*;

/**
 * Abstraktionsschicht für Kafka-Event-Publishing.
 *
 * Implementierungen:
 *   dev-Profil:  {@link de.atruvia.stablecoin.kafka.dev.DevKafkaProducer}
 *                → Spring ApplicationEventPublisher (In-Memory, kein Kafka-Broker)
 *   prod-Profil: KafkaTemplateEventProducer (spring-kafka, echter Broker)
 *                → noch nicht implementiert, Stub in INTEGRATION_TARGET_ARCHITECTURE.md
 *
 * Alle Nachrichten werden als JSON (kein Avro, keine Schema-Registry) serialisiert.
 */
public interface KafkaEventProducer {

    /**
     * Topic: stablecoin-transfers
     * Jeder Statuswechsel einer Stablecoin-Transaktion.
     */
    void publishTransferStatus(TransferStatusEvent event);

    /**
     * Topic: compliance-screening
     * Screening-Anfrage an Chainalysis-Consumer.
     */
    void publishComplianceRequest(ComplianceScreeningEvent event);

    /**
     * Topic: compliance-screening
     * Screening-Ergebnis vom Chainalysis-Consumer.
     */
    void publishComplianceResult(ComplianceScreeningEvent event);

    /**
     * Topic: yield-lifecycle
     * Yield-Position eröffnet oder aufgelöst.
     */
    void publishYieldLifecycle(YieldLifecycleEvent event);

    /**
     * Topic: stablecoin-analytics-v1
     * Read-only Datenprodukt für DWH / Lakehouse (Data Mesh).
     */
    void publishAnalytics(AnalyticsEvent event);
}
