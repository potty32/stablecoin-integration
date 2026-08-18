package de.atruvia.stablecoin.entity;

public enum TransactionType {
    OUTBOUND, INBOUND, BULK, P2P, REMITTANCE, YIELD_DEPOSIT, YIELD_REDEEM,
    INBOUND_RETURN   // Automatische Retoure: Inbound-Betrag zurück an Absender-Wallet
}
