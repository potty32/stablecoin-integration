package de.atruvia.stablecoin.client;

import java.math.BigDecimal;

public interface FxRateClient {
    BigDecimal getEurUsdRate();
}
