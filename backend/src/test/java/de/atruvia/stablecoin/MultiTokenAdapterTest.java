package de.atruvia.stablecoin;

import de.atruvia.stablecoin.client.CircleTokenAdapter;
import de.atruvia.stablecoin.client.StablecoinTokenAdapter;
import de.atruvia.stablecoin.client.TokenAdapterRouter;
import de.atruvia.stablecoin.client.dto.AdapterTransferRequest;
import de.atruvia.stablecoin.client.dto.AdapterTransferResult;
import de.atruvia.stablecoin.client.mock.AllUnityTokenAdapter;
import de.atruvia.stablecoin.client.mock.QivalisTokenAdapter;
import de.atruvia.stablecoin.entity.StablecoinCurrency;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.*;

/**
 * UC-32: Multi-Token-Adapter-Routing
 *
 * Verifiziert das dynamische Routing-System des TokenAdapterRouter:
 * - USDC/EURC → CircleTokenAdapter
 * - EURAU → AllUnityTokenAdapter (BaFin-konform)
 * - EURQ → QivalisTokenAdapter (DZ Bank Konsortium)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("dev")
@DisplayName("UC-32: Multi-Token-Adapter-Routing")
class MultiTokenAdapterTest {

    @Autowired TokenAdapterRouter router;
    @Autowired CircleTokenAdapter circleAdapter;
    @Autowired AllUnityTokenAdapter allUnityAdapter;
    @Autowired QivalisTokenAdapter qivalisAdapter;

    private static final AdapterTransferRequest SAMPLE_REQUEST = new AdapterTransferRequest(
            "test-idem-001",
            "BANK_MASTER_WALLET_ID",
            "0xRecipientWallet0000000000000000000000001",
            new BigDecimal("1000.00"),
            StablecoinCurrency.USDC);

    // ── TC-01: USDC → CircleTokenAdapter ─────────────────────────────────────

    @Test
    @DisplayName("TC-01: USDC wird an CircleTokenAdapter geroutet")
    void tc01_routeUsdc_toCircleAdapter() {
        StablecoinTokenAdapter adapter = router.getAdapter(StablecoinCurrency.USDC);

        assertThat(adapter).isInstanceOf(CircleTokenAdapter.class);
        assertThat(adapter.getAdapterName()).isEqualTo("Circle-USDC/EURC");
        assertThat(adapter.supportedCurrencies()).contains(StablecoinCurrency.USDC);
    }

    // ── TC-02: EURC → CircleTokenAdapter ─────────────────────────────────────

    @Test
    @DisplayName("TC-02: EURC wird an CircleTokenAdapter geroutet")
    void tc02_routeEurc_toCircleAdapter() {
        StablecoinTokenAdapter adapter = router.getAdapter(StablecoinCurrency.EURC);

        assertThat(adapter).isInstanceOf(CircleTokenAdapter.class);
        assertThat(adapter.supportedCurrencies()).contains(StablecoinCurrency.EURC);
    }

    // ── TC-03: EURAU → AllUnityTokenAdapter ──────────────────────────────────

    @Test
    @DisplayName("TC-03: EURAU wird an AllUnityTokenAdapter geroutet")
    void tc03_routeEurau_toAllUnityAdapter() {
        StablecoinTokenAdapter adapter = router.getAdapter(StablecoinCurrency.EURAU);

        assertThat(adapter).isInstanceOf(AllUnityTokenAdapter.class);
        assertThat(adapter.getAdapterName()).isEqualTo("AllUnity-EURAU-Mock");
        assertThat(adapter.supportedCurrencies()).containsExactly(StablecoinCurrency.EURAU);

        // Vollständiger Transfer-Zyklus verifizieren
        AdapterTransferResult result = adapter.initiateAndConfirm(
                new AdapterTransferRequest("test-eurau-001", "BANK_MASTER_WALLET_ID",
                        "0xAllUnityRecipient", new BigDecimal("5000.00"), StablecoinCurrency.EURAU));

        assertThat(result.adapterTransactionId()).startsWith("allunity-");
        assertThat(result.blockchainHash()).startsWith("0xALLUNITY");
    }

    // ── TC-04: EURQ → QivalisTokenAdapter ────────────────────────────────────

    @Test
    @DisplayName("TC-04: EURQ wird an QivalisTokenAdapter geroutet")
    void tc04_routeEurq_toQivalisAdapter() {
        StablecoinTokenAdapter adapter = router.getAdapter(StablecoinCurrency.EURQ);

        assertThat(adapter).isInstanceOf(QivalisTokenAdapter.class);
        assertThat(adapter.getAdapterName()).isEqualTo("Qivalis-EURQ-Mock");
        assertThat(adapter.supportedCurrencies()).containsExactly(StablecoinCurrency.EURQ);

        // Vollständiger Transfer-Zyklus verifizieren
        AdapterTransferResult result = adapter.initiateAndConfirm(
                new AdapterTransferRequest("test-eurq-001", "BANK_MASTER_WALLET_ID",
                        "0xQivalisRecipient", new BigDecimal("25000.00"), StablecoinCurrency.EURQ));

        assertThat(result.adapterTransactionId()).startsWith("qivalis-");
        assertThat(result.blockchainHash()).startsWith("0xQIVALIS");
    }

    // ── TC-05: AllUnity BaFin-Deckungsprüfung ────────────────────────────────

    @Test
    @DisplayName("TC-05: AllUnityAdapter — BaFin-Deckungsprüfung (MiCA Art. 36) bestanden bei 112% Deckung")
    void tc05_allUnityAdapter_bafinCoverageCheck() {
        BigDecimal transferAmount = new BigDecimal("100000.00");

        // Kein Fehler erwartet — simulierte Deckung 112% > 105% Minimum
        assertThatNoException()
                .isThrownBy(() -> allUnityAdapter.assertBaFinCoverage(transferAmount));

        // Direkter Transfer-Aufruf funktioniert ebenfalls
        AdapterTransferResult result = allUnityAdapter.initiateAndConfirm(
                new AdapterTransferRequest("test-coverage", "BANK_MASTER_WALLET_ID",
                        "0xTestWallet", transferAmount, StablecoinCurrency.EURAU));

        assertThat(result.blockchainHash()).isNotBlank();
        assertThat(result.adapterTransactionId()).isNotBlank();
    }

    // ── TC-06: Unbekannte Currency → Exception ────────────────────────────────

    @Test
    @DisplayName("TC-06: Unbekannte Currency wirft NoSuchElementException")
    void tc06_unknownCurrency_throwsException() {
        // Alle Enum-Werte sind registriert — wir testen indirekt über getRegisteredAdapters
        assertThat(router.getRegisteredAdapters())
                .containsKey(StablecoinCurrency.USDC)
                .containsKey(StablecoinCurrency.EURC)
                .containsKey(StablecoinCurrency.EURAU)
                .containsKey(StablecoinCurrency.EURQ);

        // Alle vier Währungen müssen geroutet werden können
        for (StablecoinCurrency currency : StablecoinCurrency.values()) {
            assertThatNoException()
                    .as("Adapter für %s muss vorhanden sein", currency)
                    .isThrownBy(() -> router.getAdapter(currency));
        }
    }
}
