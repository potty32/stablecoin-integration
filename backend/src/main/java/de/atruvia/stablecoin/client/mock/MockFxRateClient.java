package de.atruvia.stablecoin.client.mock;

import de.atruvia.stablecoin.client.FxRateClient;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@Profile("!prod")
public class MockFxRateClient implements FxRateClient {

    // Simulierter ECB-Referenzkurs EUR/USD
    private static final BigDecimal MOCK_EUR_USD_RATE = new BigDecimal("1.0823");

    @Override
    public BigDecimal getEurUsdRate() {
        return MOCK_EUR_USD_RATE;
    }
}
