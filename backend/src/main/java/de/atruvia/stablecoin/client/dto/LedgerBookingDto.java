package de.atruvia.stablecoin.client.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record LedgerBookingDto(
        String referenceId,
        String debitAccount,
        List<CreditEntry> creditAccounts,
        BigDecimal totalAmount,
        String currency,
        LocalDate valueDate
) {
    public record CreditEntry(String iban, BigDecimal amount, String label) {}
}
