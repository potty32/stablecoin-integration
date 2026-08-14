package de.atruvia.stablecoin.client.dto;

import java.time.LocalDateTime;

public record CircleTransferResponseDto(String id, String status, String transactionHash, LocalDateTime createDate) {}
