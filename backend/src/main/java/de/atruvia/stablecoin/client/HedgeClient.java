package de.atruvia.stablecoin.client;

import de.atruvia.stablecoin.client.dto.HedgeConfirmationDto;
import de.atruvia.stablecoin.client.dto.HedgeRequestDto;

/**
 * G-05: Interface zur DZ BANK Treasury / Market Maker API für FX-Hedging.
 * Im Dev-Profil: MockHedgeClient (loggt, gibt Dummy-Confirmation zurück).
 * Im Prod-Profil: HttpDzBankHedgeClient (ruft DZ BANK Treasury REST-API auf).
 *
 * Hedging neutralisiert das Wechselkursrisiko der Bank während des
 * 60-sekündigen Rate-Quote-Garantiefensters (MiCA Art. 45, BCBS FX Risk).
 */
public interface HedgeClient {

    /**
     * Eröffnet eine Hedging-Position beim Market Maker.
     * Wird unmittelbar nach Ausstellung einer Rate-Quote aufgerufen.
     *
     * @param request enthält: eurAmount, currency, expiresAt, txReference
     * @return Hedge-Confirmation mit hedgeId und confirmedRate
     */
    HedgeConfirmationDto hedgeCurrencyRisk(HedgeRequestDto request);

    /**
     * Schließt eine Hedging-Position.
     * Wird nach Settlement oder Ablehnung/Ablauf der Rate-Quote aufgerufen.
     *
     * @param hedgeId ID aus hedgeCurrencyRisk()
     */
    void closeHedge(String hedgeId);
}
