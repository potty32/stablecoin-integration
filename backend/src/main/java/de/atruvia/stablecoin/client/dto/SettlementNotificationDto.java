package de.atruvia.stablecoin.client.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record SettlementNotificationDto(
        UUID transactionId,
        String customerId,
        LocalDateTime settledAt,
        BigDecimal amountFiat,
        String amountStablecoin,
        String currency,
        String blockchainHash,
        BigDecimal grossRevenue,
        RevenueBreakdown breakdown
) {
    public record RevenueBreakdown(BigDecimal fxSpread, BigDecimal transactionFee, BigDecimal gasCost) {}
}
