package de.atruvia.stablecoin.kafka;

/**
 * Kafka-Topic-Konstanten der Atruvia Stablecoin Integration Platform.
 *
 * Im Dev-Profil: Simulation via Spring ApplicationEventPublisher (kein Kafka-Broker nötig).
 * Im Prod-Profil: Echte Apache-Kafka-Broker (spring-kafka-Implementierung ergänzen).
 *
 * Serialisierungsformat: Reines JSON (kein Avro, keine Schema-Registry).
 */
public final class KafkaTopics {

    private KafkaTopics() {}

    /** Alle TX-Statuswechsel — Quelle für Ledger, Notification, Audit. */
    public static final String STABLECOIN_TRANSFERS = "stablecoin-transfers";

    /** Screening-Aufträge an Chainalysis + Ergebnis-Events. */
    public static final String COMPLIANCE_SCREENING = "compliance-screening";

    /** Ereignisse über Yield-Positionen (DEPOSIT / REDEEM). */
    public static final String YIELD_LIFECYCLE = "yield-lifecycle";

    /** Read-only Aggregations-Topic für DWH / BI / Lakehouse (Data Mesh). */
    public static final String ANALYTICS_V1 = "stablecoin-analytics-v1";
}
