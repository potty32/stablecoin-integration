package de.atruvia.stablecoin.client.mock;

import de.atruvia.stablecoin.client.CircleWalletClient;
import de.atruvia.stablecoin.client.dto.CircleTransactionStatusDto;
import de.atruvia.stablecoin.client.dto.CircleTransferRequestDto;
import de.atruvia.stablecoin.client.dto.CircleTransferResponseDto;
import de.atruvia.stablecoin.client.dto.CircleWalletBalanceDto;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Profile("dev")
public class MockCircleWalletClient implements CircleWalletClient {

    @Override
    public CircleTransferResponseDto initiateTransfer(CircleTransferRequestDto request) {
        String id = "circle-tx-" + UUID.randomUUID().toString().substring(0, 8);
        return new CircleTransferResponseDto(id, "PENDING", "0xpending-" + id, LocalDateTime.now());
    }

    @Override
    public CircleTransactionStatusDto getTransactionStatus(String transactionId) {
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return new CircleTransactionStatusDto(
                transactionId,
                "COMPLETE",
                "0xabcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890",
                new CircleTransactionStatusDto.NetworkFee("0.008", "USD")
        );
    }

    @Override
    public CircleWalletBalanceDto getWalletBalance(String walletId) {
        return new CircleWalletBalanceDto(walletId, List.of(
                new CircleWalletBalanceDto.Balance("USDC", "5000000.00"),
                new CircleWalletBalanceDto.Balance("EURC", "3000000.00")
        ));
    }
}
