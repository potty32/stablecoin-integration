package de.atruvia.stablecoin.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record RemittanceResponse(
        UUID transactionId,
        String status,
        BigDecimal feeEur,
        String recipientReceivesApprox,
        String estimatedArrival,
        String trackingCode
) {}
