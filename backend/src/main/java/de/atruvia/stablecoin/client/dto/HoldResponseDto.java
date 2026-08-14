package de.atruvia.stablecoin.client.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record HoldResponseDto(String holdId, String iban, BigDecimal amount, String status, LocalDateTime expiresAt) {}
