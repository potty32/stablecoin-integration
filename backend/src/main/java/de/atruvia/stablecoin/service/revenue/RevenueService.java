package de.atruvia.stablecoin.service.revenue;

import de.atruvia.stablecoin.entity.CustomerType;
import de.atruvia.stablecoin.entity.TenantSettings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class RevenueService {

    @Value("${app.revenue.fx-spread:0.0015}")
    private BigDecimal fxSpread;

    @Value("${app.revenue.fee-b2b:2.50}")
    private BigDecimal feeB2b;

    @Value("${app.revenue.fee-b2c:0.50}")
    private BigDecimal feeB2c;

    @Value("${app.revenue.gas-cost-simulated:0.008}")
    private BigDecimal gasCostSimulated;

    /** Bestehende Methode — nutzt globale @Value-Konfiguration (Rückwärtskompatibilität). */
    public RevenueCalculation calculate(BigDecimal volume, CustomerType customerType) {
        BigDecimal spread = CustomerType.B2B.equals(customerType) ? fxSpread : fxSpread;
        BigDecimal spreadAmount = volume.multiply(spread).setScale(6, RoundingMode.HALF_UP);
        BigDecimal fee = CustomerType.B2B.equals(customerType) ? feeB2b : feeB2c;
        BigDecimal grossRevenue = spreadAmount.add(fee).subtract(gasCostSimulated).setScale(6, RoundingMode.HALF_UP);
        return new RevenueCalculation(spread, spreadAmount, fee, gasCostSimulated, grossRevenue);
    }

    /**
     * G-03: Tenant-spezifische Gebühren und Spreads.
     * Wird von B2bTransferService mit mandantenspezifischen TenantSettings aufgerufen.
     */
    public RevenueCalculation calculate(BigDecimal volume, CustomerType customerType, TenantSettings settings) {
        BigDecimal spread = CustomerType.B2B.equals(customerType)
                ? settings.getFxSpreadB2b()
                : settings.getFxSpreadB2c();
        BigDecimal fee = CustomerType.B2B.equals(customerType)
                ? settings.getFeeFlatB2bEur()
                : settings.getFeeFlatB2cEur();
        BigDecimal spreadAmount = volume.multiply(spread).setScale(6, RoundingMode.HALF_UP);
        BigDecimal grossRevenue = spreadAmount.add(fee).subtract(gasCostSimulated).setScale(6, RoundingMode.HALF_UP);
        return new RevenueCalculation(spread, spreadAmount, fee, gasCostSimulated, grossRevenue);
    }

    public record RevenueCalculation(
            BigDecimal spreadRate,
            BigDecimal spreadAmount,
            BigDecimal transactionFee,
            BigDecimal gasCost,
            BigDecimal grossRevenue
    ) {}
}
