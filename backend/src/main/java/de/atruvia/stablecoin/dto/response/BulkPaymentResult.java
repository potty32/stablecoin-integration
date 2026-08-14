package de.atruvia.stablecoin.dto.response;

import java.util.List;

public record BulkPaymentResult(
        int total,
        int successful,
        int failed,
        List<BulkRowResult> rows
) {}
