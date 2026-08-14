package de.atruvia.stablecoin.client.http;

import de.atruvia.stablecoin.client.TaurusCustodyClient;
import de.atruvia.stablecoin.client.dto.TaurusTransactionRequestDto;
import de.atruvia.stablecoin.client.dto.TaurusTransactionResponseDto;
import de.atruvia.stablecoin.exception.TaurusLimitExceededException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
@Profile("prod")
public class HttpTaurusCustodyClient implements TaurusCustodyClient {

    private final RestClient restClient;

    public HttpTaurusCustodyClient(
            RestClient.Builder builder,
            @Value("${taurus.base-url}") String baseUrl,
            @Value("${taurus.api-key}") String apiKey) {
        this.restClient = builder
                .baseUrl(baseUrl)
                .defaultHeader("X-API-Key", apiKey)
                .build();
    }

    @Override
    public TaurusTransactionResponseDto signAndSubmit(TaurusTransactionRequestDto request) {
        return restClient.post()
                .uri("/v2/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .onStatus(status -> status.value() == 403, (req, response) -> {
                    byte[] body = response.getBody().readAllBytes();
                    throw new TaurusLimitExceededException(
                            "Taurus custody rejected transaction (403 Forbidden): " + new String(body));
                })
                .onStatus(HttpStatusCode::isError, (req, response) -> {
                    byte[] body = response.getBody().readAllBytes();
                    throw new RuntimeException(
                            "Taurus API error [" + response.getStatusCode() + "]: " + new String(body));
                })
                .body(TaurusTransactionResponseDto.class);
    }
}
