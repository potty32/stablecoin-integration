package de.atruvia.stablecoin.client.dto;

import de.atruvia.stablecoin.entity.StablecoinCurrency;
import java.math.BigDecimal;

public record AdapterTransferRequest(
        String idempotencyKey,
        String sourceMasterWalletId,
        String destinationWallet,
        BigDecimal amount,
        StablecoinCurrency currency
) {}
