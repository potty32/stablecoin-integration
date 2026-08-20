package de.atruvia.stablecoin.entity;

public enum DvpEscrowStatus {
    ESCROWED,    // Betrag gesperrt, Wertpapierübertragung ausstehend
    SETTLED,     // Wertpapier erfolgreich übertragen, Stablecoins freigegeben
    CANCELLED    // Wertpapierübertragung gescheitert, Betrag an Kunden zurückgebucht
}
