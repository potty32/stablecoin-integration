package de.atruvia.stablecoin.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DevChatRequest(
        @NotBlank @Size(max = 2000) String message,
        String currentTenantId
) {}
