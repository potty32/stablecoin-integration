package de.atruvia.stablecoin.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Mandantenspezifische Konfigurationsparameter (Preise, Limits, Feature-Flags).
 * Ersetzt globale @Value-Properties in RevenueService und B2bTransferService.
 * Kein TenantEntityListener — tenant_id ist der Primary Key selbst.
 */
@Entity
@Table(name = "tenant_settings")
public class TenantSettings {

    @Id
    @Column(name = "tenant_id", length = 50)
    private String tenantId;

    // ── Preisstruktur ──────────────────────────────────────────────────────────
    @Column(name = "fx_spread_b2b", nullable = false, precision = 8, scale = 6)
    private BigDecimal fxSpreadB2b = new BigDecimal("0.001500");

    @Column(name = "fx_spread_b2c", nullable = false, precision = 8, scale = 6)
    private BigDecimal fxSpreadB2c = new BigDecimal("0.001500");

    @Column(name = "fee_flat_b2b_eur", nullable = false, precision = 18, scale = 6)
    private BigDecimal feeFlatB2bEur = new BigDecimal("2.500000");

    @Column(name = "fee_flat_b2c_eur", nullable = false, precision = 18, scale = 6)
    private BigDecimal feeFlatB2cEur = new BigDecimal("0.500000");

    @Column(name = "remittance_fee_eur", nullable = false, precision = 18, scale = 6)
    private BigDecimal remittanceFeeEur = new BigDecimal("0.500000");

    @Column(name = "micropayment_fee_eur", nullable = false, precision = 18, scale = 6)
    private BigDecimal micropaymentFeeEur = new BigDecimal("0.100000");

    // ── Grenzen ───────────────────────────────────────────────────────────────
    @Column(name = "approval_threshold_b2b", nullable = false, precision = 18, scale = 6)
    private BigDecimal approvalThresholdB2b = new BigDecimal("25000.000000");

    @Column(name = "tx_limit_single_b2b", nullable = false, precision = 18, scale = 6)
    private BigDecimal txLimitSingleB2b = new BigDecimal("25000.000000");

    @Column(name = "tx_limit_daily_b2b", nullable = false, precision = 18, scale = 6)
    private BigDecimal txLimitDailyB2b = new BigDecimal("2000000.000000");

    @Column(name = "tx_limit_single_b2c", nullable = false, precision = 18, scale = 6)
    private BigDecimal txLimitSingleB2c = new BigDecimal("5000.000000");

    @Column(name = "tx_limit_daily_b2c", nullable = false, precision = 18, scale = 6)
    private BigDecimal txLimitDailyB2c = new BigDecimal("10000.000000");

    // ── Kursschutz ────────────────────────────────────────────────────────────
    @Column(name = "rate_quote_validity_secs", nullable = false)
    private int rateQuoteValiditySecs = 60;

    @Column(name = "slippage_tolerance_bps", nullable = false)
    private int slippageToleranceBps = 100;

    // ── Geo-Compliance ────────────────────────────────────────────────────────
    @Column(name = "allowed_currencies", nullable = false, length = 50)
    private String allowedCurrencies = "USDC,EURC";

    @Column(name = "allowed_blockchains", nullable = false, length = 100)
    private String allowedBlockchains = "POLYGON";

    @Column(name = "blocked_countries")
    private String blockedCountries;

    // ── Feature-Flags ─────────────────────────────────────────────────────────
    @Column(name = "enable_yield", nullable = false)
    private boolean enableYield = true;

    @Column(name = "enable_bulk_payments", nullable = false)
    private boolean enableBulkPayments = true;

    // ── Kill Switch (Mandanten-Ebene, G-07) ───────────────────────────────────
    @Column(name = "kill_switch_active", nullable = false)
    private boolean killSwitchActive = false;

    @Column(name = "kill_switch_reason")
    private String killSwitchReason;

    @Column(name = "kill_switch_by", length = 100)
    private String killSwitchBy;

    @Column(name = "kill_switch_at")
    private LocalDateTime killSwitchAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    void onUpdate() { this.updatedAt = LocalDateTime.now(); }

    // ── Getter / Setter ───────────────────────────────────────────────────────
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public BigDecimal getFxSpreadB2b() { return fxSpreadB2b; }
    public void setFxSpreadB2b(BigDecimal v) { this.fxSpreadB2b = v; }
    public BigDecimal getFxSpreadB2c() { return fxSpreadB2c; }
    public void setFxSpreadB2c(BigDecimal v) { this.fxSpreadB2c = v; }
    public BigDecimal getFeeFlatB2bEur() { return feeFlatB2bEur; }
    public void setFeeFlatB2bEur(BigDecimal v) { this.feeFlatB2bEur = v; }
    public BigDecimal getFeeFlatB2cEur() { return feeFlatB2cEur; }
    public void setFeeFlatB2cEur(BigDecimal v) { this.feeFlatB2cEur = v; }
    public BigDecimal getRemittanceFeeEur() { return remittanceFeeEur; }
    public BigDecimal getMicropaymentFeeEur() { return micropaymentFeeEur; }
    public BigDecimal getApprovalThresholdB2b() { return approvalThresholdB2b; }
    public void setApprovalThresholdB2b(BigDecimal v) { this.approvalThresholdB2b = v; }
    public BigDecimal getTxLimitSingleB2b() { return txLimitSingleB2b; }
    public BigDecimal getTxLimitDailyB2b() { return txLimitDailyB2b; }
    public BigDecimal getTxLimitSingleB2c() { return txLimitSingleB2c; }
    public BigDecimal getTxLimitDailyB2c() { return txLimitDailyB2c; }
    public int getRateQuoteValiditySecs() { return rateQuoteValiditySecs; }
    public int getSlippageToleranceBps() { return slippageToleranceBps; }
    public void setSlippageToleranceBps(int v) { this.slippageToleranceBps = v; }
    public String getAllowedCurrencies() { return allowedCurrencies; }
    public String getAllowedBlockchains() { return allowedBlockchains; }
    public String getBlockedCountries() { return blockedCountries; }
    public boolean isEnableYield() { return enableYield; }
    public boolean isEnableBulkPayments() { return enableBulkPayments; }
    public boolean isKillSwitchActive() { return killSwitchActive; }
    public void setKillSwitchActive(boolean v) { this.killSwitchActive = v; }
    public String getKillSwitchReason() { return killSwitchReason; }
    public void setKillSwitchReason(String v) { this.killSwitchReason = v; }
    public String getKillSwitchBy() { return killSwitchBy; }
    public void setKillSwitchBy(String v) { this.killSwitchBy = v; }
    public LocalDateTime getKillSwitchAt() { return killSwitchAt; }
    public void setKillSwitchAt(LocalDateTime v) { this.killSwitchAt = v; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
