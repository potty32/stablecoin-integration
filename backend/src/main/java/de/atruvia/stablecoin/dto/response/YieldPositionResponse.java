package de.atruvia.stablecoin.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record YieldPositionResponse(
        UUID depositId,
        BigDecimal amountEur,
        BigDecimal currentValueEur,
        BigDecimal dailyYieldEur,
        BigDecimal yieldRatePercent,
        String status,
        LocalDateTime depositDate
) {}
