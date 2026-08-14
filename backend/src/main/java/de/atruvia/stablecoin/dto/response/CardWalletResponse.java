package de.atruvia.stablecoin.dto.response;

public record CardWalletResponse(
        String walletAddress,
        String balanceUsdc,
        String balanceEurc
) {}
