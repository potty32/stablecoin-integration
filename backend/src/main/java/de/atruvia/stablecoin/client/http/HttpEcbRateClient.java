package de.atruvia.stablecoin.client.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.atruvia.stablecoin.client.FxRateClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@Profile("prod")
public class HttpEcbRateClient implements FxRateClient {

    private static final Logger log = LoggerFactory.getLogger(HttpEcbRateClient.class);
    private static final String ECB_SERIES_PATH = "/service/data/EXR/D.USD.EUR.SP00.A?format=jsondata&lastNObservations=1";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final RestClient restClient;

    public HttpEcbRateClient(
            RestClient.Builder builder,
            @Value("${ecb.base-url:https://data-api.ecb.europa.eu}") String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    @Override
    public BigDecimal getEurUsdRate() {
        try {
            String json = restClient.get()
                    .uri(ECB_SERIES_PATH)
                    .retrieve()
                    .body(String.class);

            // ECB SDMX-JSON: dataSets[0].series["0:0:0:0:0"].observations["0"][0]
            JsonNode root = MAPPER.readTree(json);
            double rate = root.path("dataSets").get(0)
                    .path("series").path("0:0:0:0:0")
                    .path("observations").path("0").get(0).asDouble();

            BigDecimal result = BigDecimal.valueOf(rate).setScale(6, RoundingMode.HALF_UP);
            log.info("[FX] ECB EUR/USD rate fetched: {}", result);
            return result;
        } catch (Exception e) {
            log.error("[FX] Failed to fetch ECB EUR/USD rate, falling back to 1.0: {}", e.getMessage());
            return BigDecimal.ONE;
        }
    }
}
