package de.atruvia.stablecoin.service.revenue;

import de.atruvia.stablecoin.entity.CustomerType;
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

    public RevenueCalculation calculate(BigDecimal volume, CustomerType customerType) {
        BigDecimal spreadAmount = volume.multiply(fxSpread).setScale(6, RoundingMode.HALF_UP);
        BigDecimal fee = CustomerType.B2B.equals(customerType) ? feeB2b : feeB2c;
        BigDecimal grossRevenue = spreadAmount.add(fee).subtract(gasCostSimulated).setScale(6, RoundingMode.HALF_UP);
        return new RevenueCalculation(fxSpread, spreadAmount, fee, gasCostSimulated, grossRevenue);
    }

    public record RevenueCalculation(
            BigDecimal spreadRate,
            BigDecimal spreadAmount,
            BigDecimal transactionFee,
            BigDecimal gasCost,
            BigDecimal grossRevenue
    ) {}
}
