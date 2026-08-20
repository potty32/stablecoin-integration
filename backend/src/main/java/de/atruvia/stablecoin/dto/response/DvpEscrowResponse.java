package de.atruvia.stablecoin.dto.response;

import de.atruvia.stablecoin.entity.DvpEscrowStatus;
import de.atruvia.stablecoin.entity.StablecoinCurrency;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record DvpEscrowResponse(
        UUID            escrowId,
        DvpEscrowStatus status,
        BigDecimal      amountFiat,
        BigDecimal      amountStablecoin,
        StablecoinCurrency currency,
        String          securitiesIsin,
        BigDecimal      securitiesAmount,
        String          escrowReference,
        String          securitiesSystemId,
        BigDecimal      feeAmount,
        String          blockchainHash,
        LocalDateTime   lockedAt,
        LocalDateTime   settledAt,
        LocalDateTime   cancelledAt,
        String          cancellationReason
) {}
