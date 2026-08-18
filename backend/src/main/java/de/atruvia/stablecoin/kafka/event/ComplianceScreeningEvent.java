package de.atruvia.stablecoin.kafka.event;

import java.time.Instant;

/**
 * Topic: compliance-screening
 *
 * Zwei Event-Typen auf diesem Topic:
 *
 * 1. SCREENING_REQUESTED — B2bTransferService → Compliance-Consumer
 * {
 *   "eventId":         "uuid",
 *   "eventType":       "SCREENING_REQUESTED",
 *   "schemaVersion":   "1.0",
 *   "topic":           "compliance-screening",
 *   "timestamp":       "2026-08-18T10:00:00Z",
 *   "tenantId":        "tenant-kleine-vb",
 *   "correlationId":   "<transactionId>",
 *   "walletAddress":   "0xA100...0001",
 *   "direction":       "outgoing",
 *   "userId":          "cust-b2b-001"
 * }
 *
 * 2. SCREENING_COMPLETED — ComplianceService → B2bTransferService (reply-topic oder direkt)
 * {
 *   "eventId":         "uuid",
 *   "eventType":       "SCREENING_COMPLETED",
 *   "schemaVersion":   "1.0",
 *   "topic":           "compliance-screening",
 *   "timestamp":       "2026-08-18T10:00:01Z",
 *   "tenantId":        "tenant-kleine-vb",
 *   "correlationId":   "<transactionId>",
 *   "walletAddress":   "0xA100...0001",
 *   "direction":       "outgoing",
 *   "result":          "APPROVED",      // APPROVED | BLOCKED
 *   "riskScore":       "LOW",           // LOW | MEDIUM | HIGH
 *   "reason":          null             // Ablehnungsgrund bei BLOCKED
 * }
 */
public record ComplianceScreeningEvent(
        String eventId,
        String eventType,
        String schemaVersion,
        String topic,
        Instant timestamp,
        String tenantId,
        String correlationId,
        String walletAddress,
        String direction,
        String userId,
        // Nur bei SCREENING_COMPLETED:
        String result,
        String riskScore,
        String reason
) {
    public static ComplianceScreeningEvent requested(
            String tenantId, String correlationId,
            String walletAddress, String direction, String userId) {
        return new ComplianceScreeningEvent(
                java.util.UUID.randomUUID().toString(),
                "SCREENING_REQUESTED",
                "1.0",
                de.atruvia.stablecoin.kafka.KafkaTopics.COMPLIANCE_SCREENING,
                Instant.now(),
                tenantId, correlationId, walletAddress, direction, userId,
                null, null, null);
    }

    public static ComplianceScreeningEvent completed(
            String tenantId, String correlationId,
            String walletAddress, String direction,
            String result, String riskScore, String reason) {
        return new ComplianceScreeningEvent(
                java.util.UUID.randomUUID().toString(),
                "SCREENING_COMPLETED",
                "1.0",
                de.atruvia.stablecoin.kafka.KafkaTopics.COMPLIANCE_SCREENING,
                Instant.now(),
                tenantId, correlationId, walletAddress, direction, "SYSTEM",
                result, riskScore, reason);
    }
}
