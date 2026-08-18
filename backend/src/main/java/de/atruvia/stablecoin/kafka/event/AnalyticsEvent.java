package de.atruvia.stablecoin.kafka.event;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Topic: stablecoin-analytics-v1 (Read-only, Data-Mesh Datenprodukt)
 *
 * Aggregiertes Analyse-Event für DWH / BI / Atruvia-Lakehouse.
 * Datenkonsumenten greifen NICHT direkt auf PostgreSQL zu (RLS-Barriere).
 * tenant_id wird beim Export automatisch eingebettet → vollständige Mandantenisolation.
 *
 * JSON-Schema:
 * {
 *   "eventId":              "uuid",
 *   "eventType":            "TRANSFER_SETTLED" | "YIELD_REDEEMED" | "INBOUND_RECEIVED",
 *   "schemaVersion":        "1.0",
 *   "topic":                "stablecoin-analytics-v1",
 *   "timestamp":            "2026-08-18T10:00:00Z",
 *   "tenantId":             "tenant-kleine-vb",   // immer gesetzt für Lakehouse-Partitionierung
 *   "transactionId":        "uuid",
 *   "transactionType":      "OUTBOUND",
 *   "currency":             "USDC",
 *   "amountFiatEur":        10000.00,
 *   "amountStablecoin":     10823.00,
 *   "fxRate":               1.0823,
 *   "fxSpread":             0.0015,
 *   "grossRevenueEur":      17.492,
 *   "transactionFeeEur":    2.50,
 *   "gasCostEur":           0.008,
 *   "settledAt":            "2026-08-18T10:00:03Z",
 *   "customerType":         "B2B",
 *   "kycTier":              "TIER_3"
 * }
 */
public record AnalyticsEvent(
        String eventId,
        String eventType,
        String schemaVersion,
        String topic,
        Instant timestamp,
        String tenantId,
        String transactionId,
        String transactionType,
        String currency,
        BigDecimal amountFiatEur,
        BigDecimal amountStablecoin,
        BigDecimal fxRate,
        BigDecimal fxSpread,
        BigDecimal grossRevenueEur,
        BigDecimal transactionFeeEur,
        BigDecimal gasCostEur,
        Instant settledAt,
        String customerType,
        String kycTier
) {
    public static AnalyticsEvent ofSettledTransfer(
            String tenantId, String transactionId, String transactionType,
            String currency, BigDecimal amountFiatEur, BigDecimal amountStablecoin,
            BigDecimal fxRate, BigDecimal fxSpread,
            BigDecimal grossRevenue, BigDecimal fee, BigDecimal gas,
            Instant settledAt, String customerType, String kycTier) {
        return new AnalyticsEvent(
                java.util.UUID.randomUUID().toString(),
                "TRANSFER_SETTLED",
                "1.0",
                de.atruvia.stablecoin.kafka.KafkaTopics.ANALYTICS_V1,
                Instant.now(),
                tenantId, transactionId, transactionType, currency,
                amountFiatEur, amountStablecoin, fxRate, fxSpread,
                grossRevenue, fee, gas, settledAt, customerType, kycTier);
    }
}
