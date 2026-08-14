package de.atruvia.stablecoin.client.http;

import de.atruvia.stablecoin.client.ChainalysisClient;
import de.atruvia.stablecoin.client.dto.AddressScreenRequestDto;
import de.atruvia.stablecoin.client.dto.AddressScreenResponseDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
@Profile("prod")
public class HttpChainalysisClient implements ChainalysisClient {

    private final RestClient restClient;

    public HttpChainalysisClient(
            RestClient.Builder builder,
            @Value("${chainalysis.base-url}") String baseUrl,
            @Value("${chainalysis.api-key}") String apiKey) {
        this.restClient = builder
                .baseUrl(baseUrl)
                .defaultHeader("Token", apiKey)
                .build();
    }

    @Override
    public AddressScreenResponseDto screenAddress(AddressScreenRequestDto request) {
        return restClient.post()
                .uri("/v1/addresses/screen")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, response) -> {
                    byte[] body = response.getBody().readAllBytes();
                    throw new RuntimeException(
                            "Chainalysis API error [" + response.getStatusCode() + "]: " + new String(body));
                })
                .body(AddressScreenResponseDto.class);
    }
}
