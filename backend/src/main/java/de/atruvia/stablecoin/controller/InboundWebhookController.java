package de.atruvia.stablecoin.controller;

import de.atruvia.stablecoin.dto.request.InboundWebhookRequest;
import de.atruvia.stablecoin.dto.response.TransactionResponse;
import de.atruvia.stablecoin.service.inbound.InboundProcessingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Empfängt Blockchain-Webhooks von Circle / Taurus über eingehende Stablecoin-Zahlungen.
 * Endpunkt ist permitAll — in Produktion via HMAC-Signatur (X-Circle-Signature) abzusichern.
 */
@RestController
@RequestMapping("/api/v1/b2b/inbound")
public class InboundWebhookController {

    private final InboundProcessingService inboundProcessingService;

    public InboundWebhookController(InboundProcessingService inboundProcessingService) {
        this.inboundProcessingService = inboundProcessingService;
    }

    @PostMapping("/webhook")
    public ResponseEntity<TransactionResponse> receiveWebhook(
            @RequestBody InboundWebhookRequest request) {
        TransactionResponse response = inboundProcessingService.processInbound(request);
        return ResponseEntity.status(201).body(response);
    }
}
