package de.atruvia.stablecoin.kafka.event;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Topic: yield-lifecycle
 * Trägt Ereignisse über Yield-Sparkonten (Eröffnung und Auflösung).
 *
 * JSON-Schema:
 * {
 *   "eventId":            "uuid",
 *   "eventType":          "YIELD_DEPOSIT_CREATED" | "YIELD_REDEEMED",
 *   "schemaVersion":      "1.0",
 *   "topic":              "yield-lifecycle",
 *   "timestamp":          "2026-08-18T10:00:00Z",
 *   "tenantId":           "tenant-kleine-vb",
 *   "customerId":         "cust-b2c-001",
 *   "positionId":         "uuid",
 *   "depositTxId":        "uuid",        // DEPOSIT: TX-Referenz
 *   "redeemTxId":         "uuid",        // REDEEM: TX-Referenz, sonst null
 *   "principalEur":       2000.00,
 *   "currentValueEur":    2001.34,       // REDEEM: aktueller Wert
 *   "accruedYieldEur":    1.34,          // REDEEM: aufgelaufene Zinsen
 *   "taxWithheldEur":     0.00,          // REDEEM: Quellensteuer
 *   "netPayoutEur":       2001.34,       // REDEEM: Netto-Auszahlung
 *   "annualRatePct":      3.5,
 *   "daysHeld":           7,             // REDEEM: Anlagedauer
 *   "status":             "ACTIVE" | "CLOSED"
 * }
 */
public record YieldLifecycleEvent(
        String eventId,
        String eventType,
        String schemaVersion,
        String topic,
        Instant timestamp,
        String tenantId,
        String customerId,
        String positionId,
        String depositTxId,
        String redeemTxId,
        BigDecimal principalEur,
        BigDecimal currentValueEur,
        BigDecimal accruedYieldEur,
        BigDecimal taxWithheldEur,
        BigDecimal netPayoutEur,
        BigDecimal annualRatePct,
        Long daysHeld,
        String status
) {
    public static YieldLifecycleEvent depositCreated(
            String tenantId, String customerId, String positionId,
            String depositTxId, BigDecimal principalEur) {
        return new YieldLifecycleEvent(
                java.util.UUID.randomUUID().toString(),
                "YIELD_DEPOSIT_CREATED",
                "1.0",
                de.atruvia.stablecoin.kafka.KafkaTopics.YIELD_LIFECYCLE,
                Instant.now(),
                tenantId, customerId, positionId, depositTxId,
                null, principalEur, principalEur,
                BigDecimal.ZERO, BigDecimal.ZERO, principalEur,
                new BigDecimal("3.5"), 0L, "ACTIVE");
    }

    public static YieldLifecycleEvent redeemed(
            String tenantId, String customerId, String positionId,
            String depositTxId, String redeemTxId,
            BigDecimal principalEur, BigDecimal currentValueEur,
            BigDecimal taxWithheldEur, BigDecimal netPayoutEur,
            long daysHeld) {
        BigDecimal accrued = currentValueEur.subtract(principalEur);
        return new YieldLifecycleEvent(
                java.util.UUID.randomUUID().toString(),
                "YIELD_REDEEMED",
                "1.0",
                de.atruvia.stablecoin.kafka.KafkaTopics.YIELD_LIFECYCLE,
                Instant.now(),
                tenantId, customerId, positionId, depositTxId,
                redeemTxId, principalEur, currentValueEur,
                accrued, taxWithheldEur, netPayoutEur,
                new BigDecimal("3.5"), daysHeld, "CLOSED");
    }
}
