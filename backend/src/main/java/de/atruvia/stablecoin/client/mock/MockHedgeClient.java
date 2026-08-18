package de.atruvia.stablecoin.client.mock;

import de.atruvia.stablecoin.client.HedgeClient;
import de.atruvia.stablecoin.client.dto.HedgeConfirmationDto;
import de.atruvia.stablecoin.client.dto.HedgeRequestDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * G-05: Mock-Implementierung des HedgeClient für den Dev-Betrieb.
 * Simuliert das DZ-BANK-Treasury-Interface — loggt alle Aufrufe, gibt Dummy-Confirmations zurück.
 * Im Produktivbetrieb durch HttpDzBankHedgeClient ersetzen.
 */
@Service
@Profile("dev")
public class MockHedgeClient implements HedgeClient {

    private static final Logger log = LoggerFactory.getLogger(MockHedgeClient.class);

    @Override
    public HedgeConfirmationDto hedgeCurrencyRisk(HedgeRequestDto request) {
        String hedgeId = "hedge-" + UUID.randomUUID().toString().substring(0, 8);
        log.info("[MOCK HEDGE] Hedging-Position eröffnet: hedgeId={} amount={} {} rate={} ref={}",
                hedgeId, request.eurAmount(), request.currency(),
                request.quotedRate(), request.txReference());
        return new HedgeConfirmationDto(
                hedgeId, request.quotedRate(), request.eurAmount(),
                LocalDateTime.now(), "OPEN");
    }

    @Override
    public void closeHedge(String hedgeId) {
        log.info("[MOCK HEDGE] Hedging-Position geschlossen: hedgeId={}", hedgeId);
    }
}
