package de.atruvia.stablecoin.kafka.event;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Topic: stablecoin-transfers
 * Trägt jeden Statuswechsel einer Stablecoin-Transaktion.
 *
 * JSON-Schema (Kafka-Nachricht, reines JSON ohne Avro):
 * {
 *   "eventId":          "uuid-v4",
 *   "eventType":        "TRANSFER_STATUS_CHANGED",
 *   "schemaVersion":    "1.0",
 *   "topic":            "stablecoin-transfers",
 *   "timestamp":        "2026-08-18T10:00:00Z",
 *   "tenantId":         "tenant-kleine-vb",
 *   "userId":           "cust-b2b-001",
 *   "transactionId":    "uuid-v4",
 *   "transactionType":  "OUTBOUND",
 *   "previousStatus":   "FUNDS_HELD",
 *   "currentStatus":    "SUBMITTED",
 *   "amountFiat":       10000.00,
 *   "amountStablecoin": 10823.00,
 *   "currency":         "USDC",
 *   "blockchainHash":   "0x5c7f...",   // null bis SETTLED
 *   "grossRevenue":     17.492,         // null bis SETTLED
 *   "sourceIban":       "DE89370400440532013000"
 * }
 */
public record TransferStatusEvent(
        String eventId,
        String eventType,
        String schemaVersion,
        String topic,
        Instant timestamp,

        // Mandanten- und Benutzerkontext
        String tenantId,
        String userId,

        // Transaktionsdaten
        String transactionId,
        String transactionType,
        String previousStatus,
        String currentStatus,
        BigDecimal amountFiat,
        BigDecimal amountStablecoin,
        String currency,
        String blockchainHash,
        BigDecimal grossRevenue,
        String sourceIban
) {
    public static TransferStatusEvent of(
            String tenantId, String userId,
            String transactionId, String transactionType,
            String previousStatus, String currentStatus,
            BigDecimal amountFiat, BigDecimal amountStablecoin,
            String currency, String blockchainHash,
            BigDecimal grossRevenue, String sourceIban) {
        return new TransferStatusEvent(
                java.util.UUID.randomUUID().toString(),
                "TRANSFER_STATUS_CHANGED",
                "1.0",
                de.atruvia.stablecoin.kafka.KafkaTopics.STABLECOIN_TRANSFERS,
                Instant.now(),
                tenantId, userId,
                transactionId, transactionType,
                previousStatus, currentStatus,
                amountFiat, amountStablecoin,
                currency, blockchainHash,
                grossRevenue, sourceIban);
    }
}
