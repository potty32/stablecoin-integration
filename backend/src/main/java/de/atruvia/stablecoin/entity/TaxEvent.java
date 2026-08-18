package de.atruvia.stablecoin.entity;

import de.atruvia.stablecoin.client.dto.TaxReportResponseDto;
import de.atruvia.stablecoin.config.TenantEntityListener;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * G-02: Audit-Nachweis für jede Kapitalertrag-Meldung an das Atruvia Tax Engine (Drittsystem).
 * Speichert das Ergebnis der AtruviaTaxClient.reportCapitalGain()-Anfrage lokal.
 * Freistellungsaufträge, KapErSt-Berechnung und Finanzamt-Buchungen liegen beim Drittsystem.
 */
@Entity
@Table(name = "tax_event")
@EntityListeners(TenantEntityListener.class)
public class TaxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "yield_position_id", nullable = false)
    private UUID yieldPositionId;

    @Column(name = "redeem_tx_id")  // nullable: bei Jahresabschluss-Bewertung kein Redeem
    private UUID redeemTxId;

    @Column(name = "customer_account_id", nullable = false)
    private UUID customerAccountId;

    @Column(name = "tax_year", nullable = false)
    private int taxYear;

    @Column(name = "gross_yield_eur", nullable = false, precision = 18, scale = 6)
    private BigDecimal grossYieldEur;

    @Column(name = "tax_withheld_eur", nullable = false, precision = 18, scale = 6)
    private BigDecimal taxWithheldEur;

    @Column(name = "net_payout_eur", nullable = false, precision = 18, scale = 6)
    private BigDecimal netPayoutEur;

    @Column(name = "tax_reference_id", length = 100)
    private String taxReferenceId;

    @Column(name = "tax_status", nullable = false, length = 20)
    private String taxStatus;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "tenant_id", nullable = false, length = 50)
    private String tenantId;

    /** Factory-Methode aus Drittsystem-Response. */
    public static TaxEvent of(UUID yieldPositionId, UUID redeemTxId, CustomerAccount account,
                               BigDecimal grossYield, TaxReportResponseDto taxResponse) {
        TaxEvent e = new TaxEvent();
        e.setYieldPositionId(yieldPositionId);
        e.setRedeemTxId(redeemTxId);
        e.setCustomerAccountId(account.getId());
        e.setTaxYear(java.time.LocalDate.now().getYear());
        e.setGrossYieldEur(grossYield);
        e.setTaxWithheldEur(taxResponse.taxWithheldEur());
        e.setNetPayoutEur(taxResponse.netPayoutEur());
        e.setTaxReferenceId(taxResponse.taxReferenceId());
        e.setTaxStatus(taxResponse.status());
        return e;
    }

    public UUID getId() { return id; }
    public UUID getYieldPositionId() { return yieldPositionId; }
    public void setYieldPositionId(UUID v) { this.yieldPositionId = v; }
    public UUID getRedeemTxId() { return redeemTxId; }
    public void setRedeemTxId(UUID v) { this.redeemTxId = v; }
    public UUID getCustomerAccountId() { return customerAccountId; }
    public void setCustomerAccountId(UUID v) { this.customerAccountId = v; }
    public int getTaxYear() { return taxYear; }
    public void setTaxYear(int v) { this.taxYear = v; }
    public BigDecimal getGrossYieldEur() { return grossYieldEur; }
    public void setGrossYieldEur(BigDecimal v) { this.grossYieldEur = v; }
    public BigDecimal getTaxWithheldEur() { return taxWithheldEur; }
    public void setTaxWithheldEur(BigDecimal v) { this.taxWithheldEur = v; }
    public BigDecimal getNetPayoutEur() { return netPayoutEur; }
    public void setNetPayoutEur(BigDecimal v) { this.netPayoutEur = v; }
    public String getTaxReferenceId() { return taxReferenceId; }
    public void setTaxReferenceId(String v) { this.taxReferenceId = v; }
    public String getTaxStatus() { return taxStatus; }
    public void setTaxStatus(String v) { this.taxStatus = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
}
