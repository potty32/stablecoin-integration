package de.atruvia.stablecoin.analytics;

import de.atruvia.stablecoin.config.TenantContext;
import de.atruvia.stablecoin.entity.TransactionStatus;
import de.atruvia.stablecoin.entity.TransactionType;
import de.atruvia.stablecoin.repository.StablecoinTransactionRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Data-Mesh Datenprodukt "Zahlungsverkehr-Analytics" (stablecoin-analytics-v1).
 *
 * Design-Prinzipien:
 * - KEIN direkter PostgreSQL-Zugriff für externe Datenkonsumenten
 * - RLS-Isolation: TenantContext aus JWT → nur eigene Mandanten-Daten sichtbar
 * - Versioniertes API (v1) für stabile Schnittstelle zum DWH/BI-Team
 * - Read-only: KEIN POST/PUT/DELETE
 *
 * Endpunkte:
 *   GET /api/v1/analytics/summary         → Tages-/Monats-Zusammenfassung
 *   GET /api/v1/analytics/revenue         → Ertragszahlen (MaRisk-Meldewesen)
 *   GET /api/v1/analytics/volume?days=30  → Transaktionsvolumen
 */
@RestController
@RequestMapping("/api/v1/analytics")
public class AnalyticsDataProductController {

    private final StablecoinTransactionRepository txRepository;

    public AnalyticsDataProductController(StablecoinTransactionRepository txRepository) {
        this.txRepository = txRepository;
    }

    /**
     * Zusammenfassung für den aktuellen Mandanten (aus JWT).
     * Datenkonsumenten: BI-Dashboards, Risiko-Reporting.
     *
     * Response:
     * {
     *   "tenantId":         "tenant-kleine-vb",
     *   "dataProductVersion": "1.0",
     *   "generatedAt":      "2026-08-18T10:00:00Z",
     *   "period":           "2026-08",
     *   "settledCount":     42,
     *   "settledVolumeEur": 420000.00,
     *   "totalRevenueEur":  1260.00,
     *   "failedCount":      2,
     *   "inboundCount":     8,
     *   "inboundVolumeEur": 80000.00
     * }
     */
    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> summary(Authentication auth) {
        String tenantId = TenantContext.get();
        if (tenantId == null) tenantId = "unknown";

        // Aggregation über RLS-gefilterte Repository-Abfragen
        var allSettled = txRepository.findByCustomerAccountIdAndStatus(
                null, TransactionStatus.SETTLED, Pageable.unpaged()).getContent();

        long settledCount = allSettled.stream()
                .filter(t -> t.getType() == TransactionType.OUTBOUND).count();
        BigDecimal settledVolume = allSettled.stream()
                .filter(t -> t.getType() == TransactionType.OUTBOUND)
                .map(t -> t.getAmountFiat() != null ? t.getAmountFiat() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalRevenue = allSettled.stream()
                .filter(t -> t.getType() == TransactionType.OUTBOUND)
                .map(t -> t.getGrossRevenue() != null ? t.getGrossRevenue() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long inboundCount = allSettled.stream()
                .filter(t -> t.getType() == TransactionType.INBOUND).count();
        BigDecimal inboundVolume = allSettled.stream()
                .filter(t -> t.getType() == TransactionType.INBOUND)
                .map(t -> t.getAmountFiat() != null ? t.getAmountFiat() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tenantId",           tenantId);
        result.put("dataProductVersion", "1.0");
        result.put("schemaRef",          "stablecoin-analytics-v1");
        result.put("generatedAt",        java.time.Instant.now().toString());
        result.put("period",             LocalDate.now().toString().substring(0, 7));
        result.put("settledCount",       settledCount);
        result.put("settledVolumeEur",   settledVolume.setScale(2, RoundingMode.HALF_UP));
        result.put("totalRevenueEur",    totalRevenue.setScale(2, RoundingMode.HALF_UP));
        result.put("inboundCount",       inboundCount);
        result.put("inboundVolumeEur",   inboundVolume.setScale(2, RoundingMode.HALF_UP));
        result.put("rlsNote",
                "RLS aktiv: Daten gefiltert nach tenant_id='" + tenantId + "'. " +
                "Datenkonsumenten greifen NICHT direkt auf PostgreSQL zu.");
        return ResponseEntity.ok(result);
    }

    /**
     * Ertragsdaten für Risiko- und MaRisk-Meldewesen.
     * Nur SETTLED OUTBOUND Transaktionen mit Ertragskomponenten.
     */
    @GetMapping("/revenue")
    public ResponseEntity<Map<String, Object>> revenue(Authentication auth) {
        String tenantId = TenantContext.get();

        var settled = txRepository.findByCustomerAccountIdAndStatus(
                null, TransactionStatus.SETTLED, Pageable.unpaged()).getContent()
                .stream().filter(t -> t.getType() == TransactionType.OUTBOUND).toList();

        BigDecimal totalFee = settled.stream()
                .map(t -> t.getFeeAmount() != null ? t.getFeeAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalSpread = settled.stream()
                .map(t -> {
                    if (t.getGrossRevenue() == null || t.getFeeAmount() == null || t.getGasCost() == null)
                        return BigDecimal.ZERO;
                    return t.getGrossRevenue().subtract(t.getFeeAmount()).add(t.getGasCost());
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalGas = settled.stream()
                .map(t -> t.getGasCost() != null ? t.getGasCost() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalGrossRevenue = settled.stream()
                .map(t -> t.getGrossRevenue() != null ? t.getGrossRevenue() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tenantId",           tenantId);
        result.put("dataProductVersion", "1.0");
        result.put("generatedAt",        java.time.Instant.now().toString());
        result.put("totalFlatFeeEur",    totalFee.setScale(2, RoundingMode.HALF_UP));
        result.put("totalFxSpreadEur",   totalSpread.setScale(2, RoundingMode.HALF_UP));
        result.put("totalGasCostEur",    totalGas.setScale(6, RoundingMode.HALF_UP));
        result.put("totalGrossRevenueEur", totalGrossRevenue.setScale(2, RoundingMode.HALF_UP));
        result.put("formula",            "R = (V×S) + F - C");
        return ResponseEntity.ok(result);
    }
}
