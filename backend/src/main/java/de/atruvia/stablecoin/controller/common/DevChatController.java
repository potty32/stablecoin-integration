package de.atruvia.stablecoin.controller.common;

import de.atruvia.stablecoin.dto.request.DevChatRequest;
import de.atruvia.stablecoin.dto.response.DevChatResponse;
import de.atruvia.stablecoin.service.common.DevChatKnowledgeService;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Dev-only Chatbot-Endpunkt für den "Atruvia Stablecoin Copilot".
 *
 * Nur aktiv wenn app.security.dev-mode=true (identisch mit DevAuthController).
 * Authentifizierung: JWT erforderlich (Security-Filter-Chain greift).
 * Tenant-Kontext: wird aus dem Request-Body `currentTenantId` bezogen,
 * ergänzt um den authentifizierten Nutzer aus dem JWT (`auth.getName()`).
 */
@RestController
@RequestMapping("/api/v1/common")
@ConditionalOnProperty(name = "app.security.dev-mode", havingValue = "true")
public class DevChatController {

    private final DevChatKnowledgeService knowledgeService;

    public DevChatController(DevChatKnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    @PostMapping("/dev-chat")
    public ResponseEntity<DevChatResponse> chat(
            @RequestBody @Valid DevChatRequest request,
            Authentication auth) {
        String tenantId = request.currentTenantId() != null
                ? request.currentTenantId()
                : "tenant-default";
        DevChatResponse response = knowledgeService.answer(request.message(), tenantId);
        return ResponseEntity.ok(response);
    }
}
