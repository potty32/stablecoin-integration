package de.atruvia.stablecoin.service.fx;

import de.atruvia.stablecoin.client.FxRateClient;
import de.atruvia.stablecoin.entity.StablecoinCurrency;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class FxRateService {

    private final FxRateClient fxRateClient;

    public FxRateService(FxRateClient fxRateClient) {
        this.fxRateClient = fxRateClient;
    }

    public BigDecimal getBaseRate(StablecoinCurrency currency) {
        return switch (currency) {
            case EURC -> BigDecimal.ONE;
            case USDC -> fxRateClient.getEurUsdRate();
        };
    }
}
