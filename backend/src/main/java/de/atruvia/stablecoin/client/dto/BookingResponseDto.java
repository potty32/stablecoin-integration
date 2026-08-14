package de.atruvia.stablecoin.client.dto;

import java.time.LocalDateTime;

public record BookingResponseDto(String bookingId, String status, LocalDateTime bookedAt) {}
