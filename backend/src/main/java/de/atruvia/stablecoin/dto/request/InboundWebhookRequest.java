package de.atruvia.stablecoin.dto.request;

import java.math.BigDecimal;

/**
 * Payload eines eingehenden Circle/Taurus-Webhooks über eine empfangene Stablecoin-Zahlung.
 *
 * @param walletId       Empfänger-Wallet (Kunden-Wallet-Adresse)
 * @param amount         Empfangener Betrag in USDC oder EURC
 * @param currency       "USDC" oder "EURC"
 * @param blockchainHash On-Chain-Transaktions-Hash (für Idempotenz-Check)
 * @param senderWallet   Absender-Wallet (Gegenpartei — wird AML-gescreent)
 */
public record InboundWebhookRequest(
        String walletId,
        BigDecimal amount,
        String currency,
        String blockchainHash,
        String senderWallet
) {}
