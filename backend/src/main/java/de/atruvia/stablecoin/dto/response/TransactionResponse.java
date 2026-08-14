package de.atruvia.stablecoin.dto.response;

import de.atruvia.stablecoin.entity.StablecoinCurrency;
import de.atruvia.stablecoin.entity.TransactionStatus;
import de.atruvia.stablecoin.entity.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record TransactionResponse(
        UUID transactionId,
        TransactionType type,
        TransactionStatus status,
        BigDecimal amountFiat,
        BigDecimal amountStablecoin,
        StablecoinCurrency currency,
        String blockchainHash,
        BigDecimal grossRevenue,
        boolean requiresApproval,
        LocalDateTime createdAt,
        LocalDateTime settledAt,
        List<TimelineEntry> timeline
) {
    public record TimelineEntry(TransactionStatus status, LocalDateTime at) {}
}
