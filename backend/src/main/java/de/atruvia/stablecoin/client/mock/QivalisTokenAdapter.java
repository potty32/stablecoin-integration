package de.atruvia.stablecoin.client.mock;

import de.atruvia.stablecoin.client.StablecoinTokenAdapter;
import de.atruvia.stablecoin.client.dto.AdapterTransferRequest;
import de.atruvia.stablecoin.client.dto.AdapterTransferResult;
import de.atruvia.stablecoin.entity.StablecoinCurrency;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;

/**
 * Mock-Adapter für den Qivalis-Token EURQ (dev-Profil).
 *
 * Simuliert die Konsortial-Schnittstellen des DZ Bank Qivalis-Konsortiums.
 * Das Protokoll nutzt ein 2-of-N Multisig-Verfahren (DZ Bank als Konsortialführer),
 * bei dem mindestens 2 von N Konsortialbanken die Transaktion signieren müssen.
 *
 * In der Produktion würde dieser Adapter den Qivalis Consortium Settlement Layer
 * (Layer-2 auf Ethereum) ansprechen und den Multisig-Handshake orchestrieren.
 */
@Component
@Profile("dev")
public class QivalisTokenAdapter implements StablecoinTokenAdapter {

    private static final Logger log = LoggerFactory.getLogger(QivalisTokenAdapter.class);

    @Override
    public Set<StablecoinCurrency> supportedCurrencies() {
        return Set.of(StablecoinCurrency.EURQ);
    }

    @Override
    public String getAdapterName() {
        return "Qivalis-EURQ-Mock";
    }

    @Override
    public AdapterTransferResult initiateAndConfirm(AdapterTransferRequest request) {
        String qivalisRef = "qivalis-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String blockchainHash = "0xQIVALIS" + UUID.randomUUID().toString().replace("-", "").substring(0, 24).toUpperCase();

        log.info("[QIVALIS-ADAPTER] EURQ Transfer (2-of-N Multisig simuliert): ref={} amount={} to={}",
                qivalisRef, request.amount(), request.destinationWallet());
        log.debug("[QIVALIS-ADAPTER] Konsortial-Signatur: DZ Bank (Lead) + VR-Bank (Co-Signer) → bestätigt");

        return new AdapterTransferResult(qivalisRef, blockchainHash);
    }

    @Override
    public AdapterTransferResult initiateReturn(AdapterTransferRequest request) {
        String qivalisRef = "qivalis-return-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String blockchainHash = "0xQIVALIS-RETURN-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();

        log.info("[QIVALIS-ADAPTER] EURQ Return-Transfer: ref={} to={}", qivalisRef, request.destinationWallet());
        return new AdapterTransferResult(qivalisRef, blockchainHash);
    }
}
