package de.atruvia.stablecoin.client.http;

import de.atruvia.stablecoin.client.N8nWebhookClient;
import de.atruvia.stablecoin.client.dto.SettlementNotificationDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
@Profile("prod")
public class HttpN8nWebhookClient implements N8nWebhookClient {

    private final RestClient restClient;
    private final String webhookUrl;

    public HttpN8nWebhookClient(
            RestClient.Builder builder,
            @Value("${n8n.webhook-url}") String webhookUrl) {
        this.webhookUrl = webhookUrl;
        this.restClient = builder.build();
    }

    @Override
    public void notifySettlement(SettlementNotificationDto notification) {
        restClient.post()
                .uri(webhookUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .body(notification)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, response) -> {
                    byte[] body = response.getBody().readAllBytes();
                    throw new RuntimeException(
                            "n8n webhook error [" + response.getStatusCode() + "]: " + new String(body));
                })
                .toBodilessEntity();
    }

    @Override
    public void notifyOutboxAlert(int stuckCount, int thresholdMinutes, String oldestMessageId) {
        var payload = java.util.Map.of(
                "event", "OUTBOX_ALERT",
                "stuckCount", stuckCount,
                "thresholdMinutes", thresholdMinutes,
                "oldestMessageId", oldestMessageId
        );
        restClient.post().uri(webhookUrl).contentType(MediaType.APPLICATION_JSON).body(payload)
                .retrieve().onStatus(HttpStatusCode::isError, (req, response) -> {
                    throw new RuntimeException("n8n outbox-alert error: " + response.getStatusCode());
                }).toBodilessEntity();
    }

    @Override
    public void notifyAddressRevoked(String walletAddress, String customerId, String riskScore) {
        var payload = java.util.Map.of(
                "event", "ADDRESS_SANCTIONS_REVOKED",
                "walletAddress", walletAddress,
                "customerId", customerId,
                "riskScore", riskScore
        );
        restClient.post()
                .uri(webhookUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, response) -> {
                    byte[] body = response.getBody().readAllBytes();
                    throw new RuntimeException(
                            "n8n webhook error [" + response.getStatusCode() + "]: " + new String(body));
                })
                .toBodilessEntity();
    }
}
