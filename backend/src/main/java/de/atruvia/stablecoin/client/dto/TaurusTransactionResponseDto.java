package de.atruvia.stablecoin.client.dto;

import java.time.LocalDateTime;

public record TaurusTransactionResponseDto(String id, String status, String signature, LocalDateTime submittedAt) {}
