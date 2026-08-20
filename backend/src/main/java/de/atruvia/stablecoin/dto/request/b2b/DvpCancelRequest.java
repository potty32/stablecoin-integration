package de.atruvia.stablecoin.dto.request.b2b;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record DvpCancelRequest(
        @NotNull  UUID escrowId,
        @NotBlank String escrowReference,
        @NotBlank String cancellationReason
) {}
