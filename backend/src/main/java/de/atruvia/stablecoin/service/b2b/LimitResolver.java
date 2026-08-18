package de.atruvia.stablecoin.service.b2b;

import de.atruvia.stablecoin.entity.CustomerAccount;
import de.atruvia.stablecoin.entity.CustomerType;
import de.atruvia.stablecoin.entity.TenantSettings;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * G-08: Löst Transaktionslimits nach eindeutiger Hierarchie auf.
 *
 * Hierarchie:
 * 1. customer_account.tx_limit_single/daily (individuelle Kundenoverride, falls > 0)
 *    → darf aber niemals den TenantSettings-Maximalwert überschreiten (Bank-Obergrenze)
 * 2. TenantSettings.tx_limit_single/daily_b2b/b2c (Mandanten-Default)
 *
 * Begründung: Ein Kundenberater kann einem VIP-Kunden ein höheres Tageslimit einräumen,
 * aber die Bank-Obergrenze (TenantSettings) gilt immer als Sicherheitsnetz.
 */
@Component
public class LimitResolver {

    /**
     * Gibt das gültige Einzeltransaktion-Limit zurück.
     * customer_account.tx_limit_single = 0 → kein Override, TenantDefault gilt.
     */
    public BigDecimal resolveSingleLimit(CustomerAccount account, TenantSettings settings) {
        BigDecimal tenantMax = isBusiness(account)
                ? settings.getTxLimitSingleB2b()
                : settings.getTxLimitSingleB2c();
        return applyHierarchy(account.getTxLimitSingle(), tenantMax);
    }

    /**
     * Gibt das gültige Tageslimit zurück (gleiche Hierarchie wie Einzeltransaktion).
     */
    public BigDecimal resolveDailyLimit(CustomerAccount account, TenantSettings settings) {
        BigDecimal tenantMax = isBusiness(account)
                ? settings.getTxLimitDailyB2b()
                : settings.getTxLimitDailyB2c();
        return applyHierarchy(account.getTxLimitDaily(), tenantMax);
    }

    /**
     * Gibt den gültigen Vier-Augen-Schwellwert zurück.
     * Kommt immer aus TenantSettings — kein kundenseitiger Override.
     */
    public BigDecimal resolveApprovalThreshold(TenantSettings settings) {
        return settings.getApprovalThresholdB2b();
    }

    private BigDecimal applyHierarchy(BigDecimal customerOverride, BigDecimal tenantMax) {
        boolean hasOverride = customerOverride != null
                && customerOverride.compareTo(BigDecimal.ZERO) > 0;
        if (!hasOverride) {
            return tenantMax;
        }
        return customerOverride.min(tenantMax);  // Kundenoverride ≤ Tenant-Obergrenze
    }

    private boolean isBusiness(CustomerAccount account) {
        return CustomerType.B2B.equals(account.getCustomerType());
    }
}
