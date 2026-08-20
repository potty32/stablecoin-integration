package de.atruvia.stablecoin.client;

import de.atruvia.stablecoin.client.dto.AdapterTransferRequest;
import de.atruvia.stablecoin.client.dto.AdapterTransferResult;
import de.atruvia.stablecoin.entity.StablecoinCurrency;

import java.util.Set;

/**
 * Token-Adapter-Interface für das Multi-Issuer-Stablecoin-Framework.
 *
 * Jeder Adapter kapselt genau einen Token-Issuer (Circle, AllUnity, Qivalis).
 * Der {@link TokenAdapterRouter} leitet Transfers anhand der {@link StablecoinCurrency}
 * dynamisch an den passenden Adapter weiter.
 *
 * Erweiterung: Neuen Issuer hinzufügen → neue Impl + Bean-Registrierung genügt.
 */
public interface StablecoinTokenAdapter {

    /** Menge der vom Adapter unterstützten Währungen. */
    Set<StablecoinCurrency> supportedCurrencies();

    /** Menschenlesbarer Adapter-Name für Logs und Metriken. */
    String getAdapterName();

    /**
     * Initiiert einen Ausgangs-Transfer und wartet auf Blockchain-Bestätigung.
     * Gibt den finalen {@link AdapterTransferResult} mit gesetztem blockchainHash zurück.
     * Implementierungen dürfen intern pollen (Circle) oder sofort settled zurückgeben (AllUnity, Qivalis-Mock).
     */
    AdapterTransferResult initiateAndConfirm(AdapterTransferRequest request);

    /**
     * Initiiert einen Rück-Transfer (Inbound-Return-Pfad).
     * Semantisch identisch zu {@link #initiateAndConfirm}, aber Implementierungen
     * können abweichende Chain-Einstellungen oder Routing nutzen.
     */
    AdapterTransferResult initiateReturn(AdapterTransferRequest request);
}
