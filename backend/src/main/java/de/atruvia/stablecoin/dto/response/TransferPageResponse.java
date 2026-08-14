package de.atruvia.stablecoin.dto.response;

import java.util.List;

public record TransferPageResponse(
        List<TransactionResponse> content,
        long totalElements,
        int totalPages,
        int page,
        int size
) {}
