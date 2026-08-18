package de.atruvia.stablecoin.entity;

public enum TransactionStatus {
    CREATED,            // Transaktion initialisiert
    PENDING_APPROVAL,   // Wartet auf Vier-Augen-Freigabe
    APPROVED,           // Zweitfreigabe erteilt
    REJECTED,           // Zweitfreigabe abgelehnt (terminal)
    EXPIRED,            // Freigabefrist abgelaufen (terminal)
    COMPLIANCE_CHECKED, // AML/Whitelist erfolgreich
    FUNDS_HELD,         // EUR-Betrag im Kernbanksystem gesperrt
    SUBMITTED,          // An Taurus/Circle/Blockchain übergeben
    SETTLED,            // Erfolgreich abgeschlossen und verbucht
    REDEEMED,             // Yield-Anlage erfolgreich aufgelöst (terminal)
    FAILED,               // Technischer oder fachlicher Abbruch mit Rollback (terminal)

    // --- Inbound-Pfad ---
    INCOMING,             // Zahlungseingang auf Blockchain registriert (Webhook empfangen)
    COMPLIANCE_PENDING,   // Post-Receive AML-Prüfung (Chainalysis) läuft
    COMPLIANCE_APPROVED,  // Geldwäscheprüfung erfolgreich → Gutschrift folgt
    COMPLIANCE_REJECTED,  // Geldwäscheverdacht, Gelder blockiert → FAILED (terminal via FAILED)

    // --- Enterprise R-Transaktionen und Sammelkonto ---
    UNASSIGNED,           // Wallet-Adresse keinem Konto zuordenbar — parkt auf Sammelkonto (terminal)
    RETURNED              // Inbound-Retoure abgeschlossen: Betrag an Absender zurückgesendet (terminal)
}
