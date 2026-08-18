package de.atruvia.stablecoin.dto.request;

import java.util.UUID;

/**
 * Admin-Request für die manuelle Zuordnung einer UNASSIGNED-Transaktion
 * vom Sammelkonto auf ein echtes Kundenkonto.
 */
public record ReassignTransactionRequest(
        UUID transactionId,
        String targetIban
) {}
