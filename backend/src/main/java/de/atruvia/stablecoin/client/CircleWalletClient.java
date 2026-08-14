package de.atruvia.stablecoin.client;

import de.atruvia.stablecoin.client.dto.CircleTransactionStatusDto;
import de.atruvia.stablecoin.client.dto.CircleTransferRequestDto;
import de.atruvia.stablecoin.client.dto.CircleTransferResponseDto;
import de.atruvia.stablecoin.client.dto.CircleWalletBalanceDto;

public interface CircleWalletClient {
    CircleTransferResponseDto initiateTransfer(CircleTransferRequestDto request);
    CircleTransactionStatusDto getTransactionStatus(String transactionId);
    CircleWalletBalanceDto getWalletBalance(String walletId);
}
