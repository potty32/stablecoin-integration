package de.atruvia.stablecoin.service.b2c;

import de.atruvia.stablecoin.client.AtruviaTaxClient;
import de.atruvia.stablecoin.client.dto.TaxReportRequestDto;
import de.atruvia.stablecoin.client.dto.TaxReportResponseDto;
import de.atruvia.stablecoin.config.TenantContext;
import de.atruvia.stablecoin.entity.TaxEvent;
import de.atruvia.stablecoin.entity.YieldPosition;
import de.atruvia.stablecoin.repository.TaxEventRepository;
import de.atruvia.stablecoin.repository.YieldPositionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * G-15: Jahresabschluss-Bewertung für offene Yield-Positionen.
 *
 * Läuft am 31.12. um 23:30 Uhr. Bewertet alle ACTIVE YieldPositions nach
 * EStG §11 (Realisationsprinzip) und HGB §252 (Jahresabschluss).
 *
 * Die Steuer-Meldung geht an die Atruvia Tax Engine (Drittsystem),
 * die die vorläufige Jahressteuer berechnet und vormerkt.
 * Beim tatsächlichen Redeem wird dann mit der gemeldeten Voraus-Bewertung abgestimmt.
 */
@Service
public class YieldYearEndService {

    private static final Logger log = LoggerFactory.getLogger(YieldYearEndService.class);

    private final YieldPositionRepository yieldPositionRepository;
    private final TaxEventRepository taxEventRepository;
    private final AtruviaTaxClient atruviaTaxClient;
    private final B2cYieldService yieldService;

    public YieldYearEndService(YieldPositionRepository yieldPositionRepository,
                                TaxEventRepository taxEventRepository,
                                AtruviaTaxClient atruviaTaxClient,
                                B2cYieldService yieldService) {
        this.yieldPositionRepository = yieldPositionRepository;
        this.taxEventRepository = taxEventRepository;
        this.atruviaTaxClient = atruviaTaxClient;
        this.yieldService = yieldService;
    }

    /**
     * Täglich am 31.12. um 23:30 Uhr.
     * Kann auch manuell via Admin-Endpoint ausgelöst werden.
     */
    @Scheduled(cron = "0 30 23 31 12 ?")
    public void runYearEndValuation() {
        int currentYear = LocalDate.now().getYear();
        log.info("[YEAR-END] Jahresabschluss-Bewertung gestartet für Jahr {}", currentYear);

        List<YieldPosition> positions = yieldPositionRepository.findActiveNotValuedInYear(currentYear);
        log.info("[YEAR-END] {} offene Position(en) zur Bewertung", positions.size());

        int success = 0, errors = 0;
        for (YieldPosition position : positions) {
            try {
                valuatePosition(position, currentYear);
                success++;
            } catch (Exception e) {
                errors++;
                log.error("[YEAR-END] Fehler bei position={}: {}", position.getId(), e.getMessage(), e);
            }
        }

        log.info("[YEAR-END] Abgeschlossen: {} bewertet, {} Fehler", success, errors);
    }

    @Transactional
    public void valuatePosition(YieldPosition position, int taxYear) {
        long days = ChronoUnit.DAYS.between(position.getDepositedAt(), LocalDateTime.now());
        BigDecimal currentValue = yieldService.computeCurrentValue(position.getPrincipal(), days);
        BigDecimal accruedYield = currentValue.subtract(position.getPrincipal())
                .max(BigDecimal.ZERO);

        String tenantId = position.getTenantId();
        TenantContext.set(tenantId);
        try {
            // Steuer-Meldung an Atruvia Tax Engine (Jahres-Vorausschau)
            TaxReportResponseDto taxReport = atruviaTaxClient.reportCapitalGain(
                    new TaxReportRequestDto(
                            position.getCustomerAccount().getCustomerId(),
                            tenantId,
                            accruedYield,
                            taxYear,
                            "year-end-" + position.getId() + "-" + taxYear));

            // TaxEvent für Audit-Nachweis (kein redeemTxId — Jahresabschluss, kein Redeem)
            TaxEvent taxEvent = new TaxEvent();
            taxEvent.setYieldPositionId(position.getId());
            taxEvent.setCustomerAccountId(position.getCustomerAccount().getId());
            taxEvent.setTaxYear(taxYear);
            taxEvent.setGrossYieldEur(accruedYield);
            taxEvent.setTaxWithheldEur(taxReport.taxWithheldEur());
            taxEvent.setNetPayoutEur(taxReport.netPayoutEur());
            taxEvent.setTaxReferenceId(taxReport.taxReferenceId());
            taxEvent.setTaxStatus("YEAR_END_" + taxReport.status());
            TaxEvent savedTaxEvent = taxEventRepository.save(taxEvent);

            // Position als bewertet markieren
            position.setYearEndValuationEur(currentValue);
            position.setYearEndTaxEventId(savedTaxEvent.getId());
            position.setLastValuedYear(taxYear);
            yieldPositionRepository.save(position);

            log.info("[YEAR-END] Position={} bewertet: accruedYield={} taxRef={} status={}",
                    position.getId(), accruedYield, taxReport.taxReferenceId(), taxReport.status());
        } finally {
            TenantContext.clear();
        }
    }
}
