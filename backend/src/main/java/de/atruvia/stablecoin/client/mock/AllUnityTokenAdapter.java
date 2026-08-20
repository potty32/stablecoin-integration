package de.atruvia.stablecoin.client.mock;

import de.atruvia.stablecoin.client.StablecoinTokenAdapter;
import de.atruvia.stablecoin.client.dto.AdapterTransferRequest;
import de.atruvia.stablecoin.client.dto.AdapterTransferResult;
import de.atruvia.stablecoin.entity.StablecoinCurrency;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

/**
 * Mock-Adapter für AllUnity EURAU (dev-Profil).
 *
 * Simuliert die AllUnity API-Struktur inklusive BaFin-konformer Deckungsprüfung
 * gemäß MiCA Art. 36: Deckungsreserve muss >= Transfervolumen × 1.05 sein.
 *
 * In der Produktion würde dieser Adapter durch einen HTTP-Client ersetzt werden,
 * der gegen die AllUnity Settlement API (REST/ISO 20022 MX) kommuniziert.
 */
@Component
@Profile("dev")
public class AllUnityTokenAdapter implements StablecoinTokenAdapter {

    private static final Logger log = LoggerFactory.getLogger(AllUnityTokenAdapter.class);
    private static final BigDecimal SIMULATED_COVERAGE_RATIO = new BigDecimal("1.12"); // 112% Deckung
    private static final BigDecimal REQUIRED_COVERAGE_RATIO  = new BigDecimal("1.05"); // MiCA Art. 36 Minimum

    @Override
    public Set<StablecoinCurrency> supportedCurrencies() {
        return Set.of(StablecoinCurrency.EURAU);
    }

    @Override
    public String getAdapterName() {
        return "AllUnity-EURAU-Mock";
    }

    @Override
    public AdapterTransferResult initiateAndConfirm(AdapterTransferRequest request) {
        assertBaFinCoverage(request.amount());

        String allUnityRef = "allunity-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String blockchainHash = "0xALLUNITY" + UUID.randomUUID().toString().replace("-", "").substring(0, 24).toUpperCase();

        log.info("[ALLUNITY-ADAPTER] EURAU Transfer: ref={} amount={} EUR to={}",
                allUnityRef, request.amount(), request.destinationWallet());

        return new AdapterTransferResult(allUnityRef, blockchainHash);
    }

    @Override
    public AdapterTransferResult initiateReturn(AdapterTransferRequest request) {
        String allUnityRef = "allunity-return-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String blockchainHash = "0xALLUNITY-RETURN-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();

        log.info("[ALLUNITY-ADAPTER] EURAU Return-Transfer: ref={} to={}", allUnityRef, request.destinationWallet());
        return new AdapterTransferResult(allUnityRef, blockchainHash);
    }

    /**
     * BaFin-Deckungsprüfung (MiCA Art. 36).
     * Simuliert: AllUnity-Reserve = Transferbetrag × SIMULATED_COVERAGE_RATIO.
     * Wirft IllegalStateException wenn Deckungsquote unter Minimum fällt.
     */
    public void assertBaFinCoverage(BigDecimal transferAmount) {
        BigDecimal simulatedReserve = transferAmount.multiply(SIMULATED_COVERAGE_RATIO);
        BigDecimal requiredReserve  = transferAmount.multiply(REQUIRED_COVERAGE_RATIO);

        if (simulatedReserve.compareTo(requiredReserve) < 0) {
            throw new IllegalStateException(String.format(
                    "[ALLUNITY] BaFin-Deckungsprüfung fehlgeschlagen: Reserve=%.2f < Erforderlich=%.2f (MiCA Art. 36)",
                    simulatedReserve, requiredReserve));
        }
        log.debug("[ALLUNITY-ADAPTER] Deckungsprüfung OK: Reserve={}% (Minimum=105%)",
                SIMULATED_COVERAGE_RATIO.multiply(BigDecimal.valueOf(100)).intValue());
    }
}
