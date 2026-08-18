package de.atruvia.stablecoin.client.mock;

import de.atruvia.stablecoin.client.AtruviaTaxClient;
import de.atruvia.stablecoin.client.dto.TaxReportRequestDto;
import de.atruvia.stablecoin.client.dto.TaxReportResponseDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * G-02: Mock-Implementierung des AtruviaTaxClient für den Dev-Betrieb.
 *
 * Simulationslogik (vereinfacht):
 * - Freistellungsauftrag: 1.000 EUR Jahresfreibetrag
 * - Jahresertrag-Tracker: nicht persistent (gilt pro JVM-Instanz / Test-Lauf)
 * - Bis 1.000 EUR Jahresertrag: FSA_COVERED, taxWithheld=0
 * - Über 1.000 EUR: TAX_APPLIED, KapErSt 25% + SoliZ 5,5% auf den steuerpflichtigen Teil
 *
 * Im Produktivbetrieb durch HttpAtruviaTaxClient ersetzen.
 */
@Service
@Profile("dev")
public class MockAtruviaTaxClient implements AtruviaTaxClient {

    private static final Logger log = LoggerFactory.getLogger(MockAtruviaTaxClient.class);

    private static final BigDecimal FSA_DEFAULT    = new BigDecimal("1000.00");
    private static final BigDecimal KAPEST_RATE    = new BigDecimal("0.25");
    private static final BigDecimal SOLIZ_RATE     = new BigDecimal("0.055");

    @Override
    public TaxReportResponseDto reportCapitalGain(TaxReportRequestDto request) {
        String taxRefId = "TAX-MOCK-" + UUID.randomUUID().toString().substring(0, 8);
        BigDecimal grossYield = request.grossYieldEur();

        // Vereinfachte FSA-Simulation: erstes 1.000 EUR steuerfrei (kein persistenter State)
        BigDecimal taxableAmount = grossYield.subtract(FSA_DEFAULT).max(BigDecimal.ZERO);

        BigDecimal kapEst    = taxableAmount.multiply(KAPEST_RATE).setScale(2, RoundingMode.HALF_UP);
        BigDecimal soliZ     = kapEst.multiply(SOLIZ_RATE).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalTax  = kapEst.add(soliZ);
        BigDecimal netPayout = grossYield.subtract(totalTax).setScale(6, RoundingMode.HALF_UP);

        String status;
        if (taxableAmount.compareTo(BigDecimal.ZERO) == 0) {
            status = "FSA_COVERED";
        } else if (grossYield.compareTo(FSA_DEFAULT) > 0 && taxableAmount.compareTo(grossYield) < 0) {
            status = "PARTIAL_FSA";
        } else {
            status = "TAX_APPLIED";
        }

        log.info("[MOCK TAX] customerId={} grossYield={} taxable={} kapEst={} soliZ={} net={} status={}",
                request.customerId(), grossYield, taxableAmount, kapEst, soliZ, netPayout, status);

        return new TaxReportResponseDto(taxRefId, totalTax, netPayout, status);
    }
}
