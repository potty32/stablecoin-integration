package de.atruvia.stablecoin.client;

import de.atruvia.stablecoin.client.dto.TaxReportRequestDto;
import de.atruvia.stablecoin.client.dto.TaxReportResponseDto;

/**
 * G-02: Drittsystem-Interface zur Atruvia Tax Engine (Kapitalertragsteuer).
 *
 * Die Atruvia Tax Engine übernimmt:
 * - Verwaltung der Freistellungsaufträge (FSA) pro Kunde und Steuerjahr
 * - Berechnung KapErSt (25%) + Solidaritätszuschlag (5,5%) + Kirchensteuer (ggf.)
 * - Buchung der Steuer an das Finanzamt-Verrechnungskonto
 * - Erstellung der jährlichen Steuerbescheinigungen
 *
 * Diese Plattform meldet nur den Brutto-Kapitalertrag und erhält die Netto-Auszahlung zurück.
 * Keine eigene Steuerberechnung oder FSA-Verwaltung in dieser Plattform.
 */
public interface AtruviaTaxClient {

    /**
     * Meldet einen Kapitalertrag (z.B. Yield-Redeem) an die Atruvia Tax Engine.
     * Das Drittsystem berechnet die Steuer unter Berücksichtigung des Freistellungsauftrags
     * und gibt die Netto-Auszahlung zurück.
     *
     * @param request Brutto-Kapitalertrag + Kunden- und Mandanten-Kontext
     * @return Steuer-Details + Netto-Auszahlungsbetrag für Ledger-Buchung
     */
    TaxReportResponseDto reportCapitalGain(TaxReportRequestDto request);
}
