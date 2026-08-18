package de.atruvia.stablecoin.client.dto;

import java.math.BigDecimal;

/**
 * G-02: Antwort des Atruvia Tax Engine auf eine Kapitalertragsmeldung.
 * Enthält die Nettovergütung (nach Steuerabzug) für die Ledger-Buchung.
 */
public record TaxReportResponseDto(
        String taxReferenceId,    // Referenz-ID beim Atruvia Tax Engine (Audit Trail)
        BigDecimal taxWithheldEur, // Einbehaltene Steuer (KapErSt + SoliZ + ggf. KiSt)
        BigDecimal netPayoutEur,   // Netto-Auszahlung an Kunden
        String status              // "TAX_APPLIED" | "FSA_COVERED" | "PARTIAL_FSA"
) {}
