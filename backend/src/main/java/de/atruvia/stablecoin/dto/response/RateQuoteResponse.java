package de.atruvia.stablecoin.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record RateQuoteResponse(
        UUID quoteId,
        BigDecimal sourceAmount,
        String targetAmount,
        BigDecimal rate,
        BigDecimal spreadPercent,
        BigDecimal fee,
        LocalDateTime validUntil,
        long lockedForSeconds
) {}
