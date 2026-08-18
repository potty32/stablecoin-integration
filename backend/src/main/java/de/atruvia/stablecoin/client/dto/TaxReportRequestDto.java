package de.atruvia.stablecoin.client.dto;

import java.math.BigDecimal;

/**
 * G-02: Anfrage an Atruvia Tax Engine (Drittsystem) zur Meldung eines Kapitalertrags.
 * Das Drittsystem verwaltet Freistellungsaufträge, KapErSt-Berechnung und Finanzamt-Buchungen.
 */
public record TaxReportRequestDto(
        String customerId,      // Kunden-ID für FSA-Lookup im Drittsystem
        String tenantId,        // Mandanten-ID für Reporting
        BigDecimal grossYieldEur, // Brutto-Kapitalertrag (vor Steuer)
        int taxYear,            // Steuerjahr (z.B. 2026)
        String referenceId      // Plattform-interne Referenz (z.B. "redeem-<positionId>")
) {}
