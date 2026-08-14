package de.atruvia.stablecoin.dto.request.b2b;

import jakarta.validation.constraints.NotBlank;

public record ApproveTransferRequest(@NotBlank String approverId) {}
