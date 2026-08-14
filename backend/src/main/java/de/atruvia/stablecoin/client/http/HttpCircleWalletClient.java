package de.atruvia.stablecoin.client.http;

import de.atruvia.stablecoin.client.CircleWalletClient;
import de.atruvia.stablecoin.client.dto.CircleTransactionStatusDto;
import de.atruvia.stablecoin.client.dto.CircleTransferRequestDto;
import de.atruvia.stablecoin.client.dto.CircleTransferResponseDto;
import de.atruvia.stablecoin.client.dto.CircleWalletBalanceDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.IOException;

@Service
@Profile("prod")
public class HttpCircleWalletClient implements CircleWalletClient {

    private final RestClient restClient;
    @SuppressWarnings("unused")
    private final String masterWalletId;

    public HttpCircleWalletClient(
            RestClient.Builder builder,
            @Value("${circle.base-url}") String baseUrl,
            @Value("${circle.api-key}") String apiKey,
            @Value("${circle.wallet-id}") String masterWalletId) {
        this.masterWalletId = masterWalletId;
        this.restClient = builder
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
    }

    @Override
    public CircleTransferResponseDto initiateTransfer(CircleTransferRequestDto request) {
        return restClient.post()
                .uri("/v1/w3s/developer/transactions/transfer")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, response) -> {
                    byte[] body = response.getBody().readAllBytes();
                    throw new RuntimeException(
                            "Circle API error [" + response.getStatusCode() + "]: " + new String(body));
                })
                .body(CircleTransferResponseDto.class);
    }

    @Override
    public CircleTransactionStatusDto getTransactionStatus(String transactionId) {
        return restClient.get()
                .uri("/v1/w3s/developer/transactions/{txId}", transactionId)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, response) -> {
                    byte[] body = response.getBody().readAllBytes();
                    throw new RuntimeException(
                            "Circle API error [" + response.getStatusCode() + "]: " + new String(body));
                })
                .body(CircleTransactionStatusDto.class);
    }

    @Override
    public CircleWalletBalanceDto getWalletBalance(String walletId) {
        return restClient.get()
                .uri("/v1/w3s/wallets/{walletId}/balances", walletId)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, response) -> {
                    byte[] body = response.getBody().readAllBytes();
                    throw new RuntimeException(
                            "Circle API error [" + response.getStatusCode() + "]: " + new String(body));
                })
                .body(CircleWalletBalanceDto.class);
    }
}
