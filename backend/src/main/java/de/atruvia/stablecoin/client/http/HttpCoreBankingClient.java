package de.atruvia.stablecoin.client.http;

import de.atruvia.stablecoin.client.CoreBankingClient;
import de.atruvia.stablecoin.client.dto.AccountBalanceDto;
import de.atruvia.stablecoin.client.dto.BookingResponseDto;
import de.atruvia.stablecoin.client.dto.CreateHoldDto;
import de.atruvia.stablecoin.client.dto.HoldResponseDto;
import de.atruvia.stablecoin.client.dto.LedgerBookingDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
@Profile("prod")
public class HttpCoreBankingClient implements CoreBankingClient {

    private final RestClient restClient;

    public HttpCoreBankingClient(
            RestClient.Builder builder,
            @Value("${core-banking.base-url}") String baseUrl,
            @Value("${core-banking.api-key}") String apiKey) {
        this.restClient = builder
                .baseUrl(baseUrl)
                .defaultHeader("X-API-Key", apiKey)
                .build();
    }

    @Override
    public AccountBalanceDto getAccountBalance(String iban) {
        return restClient.get()
                .uri("/api/v1/accounts/{iban}/balance", iban)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, response) -> {
                    byte[] body = response.getBody().readAllBytes();
                    throw new RuntimeException(
                            "Core Banking API error [" + response.getStatusCode() + "]: " + new String(body));
                })
                .body(AccountBalanceDto.class);
    }

    @Override
    public HoldResponseDto createHold(String iban, CreateHoldDto request) {
        return restClient.post()
                .uri("/api/v1/accounts/{iban}/holds", iban)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, response) -> {
                    byte[] body = response.getBody().readAllBytes();
                    throw new RuntimeException(
                            "Core Banking API error [" + response.getStatusCode() + "]: " + new String(body));
                })
                .body(HoldResponseDto.class);
    }

    @Override
    public void releaseHold(String holdId) {
        restClient.delete()
                .uri("/api/v1/holds/{holdId}", holdId)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, response) -> {
                    byte[] body = response.getBody().readAllBytes();
                    throw new RuntimeException(
                            "Core Banking releaseHold error [" + response.getStatusCode() + "]: " + new String(body));
                })
                .toBodilessEntity();
    }

    @Override
    public BookingResponseDto createLedgerBooking(LedgerBookingDto request) {
        return restClient.post()
                .uri("/api/v1/ledger/bookings")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, response) -> {
                    byte[] body = response.getBody().readAllBytes();
                    throw new RuntimeException(
                            "Core Banking API error [" + response.getStatusCode() + "]: " + new String(body));
                })
                .body(BookingResponseDto.class);
    }

    @Override
    public BookingResponseDto reverseBooking(String originalBookingReference, String reason) {
        return restClient.post()
                .uri("/api/v1/ledger/bookings/{ref}/reverse", originalBookingReference)
                .contentType(MediaType.APPLICATION_JSON)
                .body(java.util.Map.of("reason", reason))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, response) -> {
                    byte[] body = response.getBody().readAllBytes();
                    throw new RuntimeException(
                            "Core Banking reverseBooking error [" + response.getStatusCode() + "]: " + new String(body));
                })
                .body(BookingResponseDto.class);
    }
}
