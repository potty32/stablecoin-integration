package de.atruvia.stablecoin.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.atruvia.stablecoin.dto.request.InboundWebhookRequest;
import de.atruvia.stablecoin.dto.response.TransactionResponse;
import de.atruvia.stablecoin.service.compliance.WebhookSignatureService;
import de.atruvia.stablecoin.service.inbound.InboundProcessingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

/**
 * Empfängt Blockchain-Webhooks von Circle / Taurus über eingehende Stablecoin-Zahlungen.
 * Endpunkt ist permitAll — Absicherung erfolgt via HMAC-Signatur (X-Circle-Signature).
 * Dev-Profil: fehlende Signatur erzeugt Warnung. Prod-Profil: HTTP 401 (AUTH_002) bei fehlendem/ungültigem Header.
 */
@RestController
@RequestMapping("/api/v1/b2b/inbound")
public class InboundWebhookController {

    private final InboundProcessingService inboundProcessingService;
    private final WebhookSignatureService webhookSignatureService;
    private final ObjectMapper objectMapper;

    public InboundWebhookController(InboundProcessingService inboundProcessingService,
                                    WebhookSignatureService webhookSignatureService,
                                    ObjectMapper objectMapper) {
        this.inboundProcessingService = inboundProcessingService;
        this.webhookSignatureService = webhookSignatureService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/webhook")
    public ResponseEntity<TransactionResponse> receiveWebhook(
            @RequestHeader(value = "X-Circle-Signature", required = false) String signature,
            @RequestBody String rawBody) throws IOException {

        webhookSignatureService.verify(signature, rawBody);

        InboundWebhookRequest request = objectMapper.readValue(rawBody, InboundWebhookRequest.class);
        TransactionResponse response = inboundProcessingService.processInbound(request);
        return ResponseEntity.status(201).body(response);
    }
}
