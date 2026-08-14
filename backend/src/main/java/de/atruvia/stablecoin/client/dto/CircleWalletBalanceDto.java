package de.atruvia.stablecoin.client.dto;

import java.util.List;

public record CircleWalletBalanceDto(String walletId, List<Balance> balances) {
    public record Balance(String currency, String amount) {}
}
