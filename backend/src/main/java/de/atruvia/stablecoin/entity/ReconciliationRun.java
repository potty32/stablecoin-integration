package de.atruvia.stablecoin.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * G-04: Ergebnis eines täglichen Soll/Haben-Abgleichs zwischen Fiat-Ledger und On-Chain-Salden.
 * AT 7.2 MaRisk, §25a KWG, §238 HGB.
 */
@Entity
@Table(name = "reconciliation_run",
        uniqueConstraints = @UniqueConstraint(columnNames = {"run_date", "tenant_id"}))
public class ReconciliationRun {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "run_date", nullable = false)
    private LocalDate runDate;

    @Column(name = "tenant_id", nullable = false, length = 50)
    private String tenantId;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "PENDING";

    // Fiat-Seite
    @Column(name = "fiat_settled_count", nullable = false)
    private int fiatSettledCount = 0;

    @Column(name = "fiat_settled_total", nullable = false, precision = 18, scale = 6)
    private BigDecimal fiatSettledTotal = BigDecimal.ZERO;

    @Column(name = "fiat_inbound_total", nullable = false, precision = 18, scale = 6)
    private BigDecimal fiatInboundTotal = BigDecimal.ZERO;

    @Column(name = "fiat_fees_collected", nullable = false, precision = 18, scale = 6)
    private BigDecimal fiatFeesCollected = BigDecimal.ZERO;

    // On-Chain-Seite (Circle API Snapshot)
    @Column(name = "onchain_usdc_balance", precision = 18, scale = 6)
    private BigDecimal onchainUsdcBalance;

    @Column(name = "onchain_eurc_balance", precision = 18, scale = 6)
    private BigDecimal onchainEurcBalance;

    @Column(name = "onchain_snapshot_at")
    private LocalDateTime onchainSnapshotAt;

    // Ergebnis
    @Column(name = "discrepancy_eur", precision = 18, scale = 6)
    private BigDecimal discrepancyEur;

    @Column(name = "discrepancy_threshold", nullable = false, precision = 18, scale = 6)
    private BigDecimal discrepancyThreshold = new BigDecimal("0.010000");

    @Column(name = "alerts_generated", nullable = false)
    private int alertsGenerated = 0;

    @Column(name = "notes")
    private String notes;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    // Getter / Setter
    public UUID getId() { return id; }
    public LocalDate getRunDate() { return runDate; }
    public void setRunDate(LocalDate runDate) { this.runDate = runDate; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getFiatSettledCount() { return fiatSettledCount; }
    public void setFiatSettledCount(int fiatSettledCount) { this.fiatSettledCount = fiatSettledCount; }
    public BigDecimal getFiatSettledTotal() { return fiatSettledTotal; }
    public void setFiatSettledTotal(BigDecimal fiatSettledTotal) { this.fiatSettledTotal = fiatSettledTotal; }
    public BigDecimal getFiatInboundTotal() { return fiatInboundTotal; }
    public void setFiatInboundTotal(BigDecimal fiatInboundTotal) { this.fiatInboundTotal = fiatInboundTotal; }
    public BigDecimal getFiatFeesCollected() { return fiatFeesCollected; }
    public void setFiatFeesCollected(BigDecimal fiatFeesCollected) { this.fiatFeesCollected = fiatFeesCollected; }
    public BigDecimal getOnchainUsdcBalance() { return onchainUsdcBalance; }
    public void setOnchainUsdcBalance(BigDecimal onchainUsdcBalance) { this.onchainUsdcBalance = onchainUsdcBalance; }
    public BigDecimal getOnchainEurcBalance() { return onchainEurcBalance; }
    public void setOnchainEurcBalance(BigDecimal onchainEurcBalance) { this.onchainEurcBalance = onchainEurcBalance; }
    public LocalDateTime getOnchainSnapshotAt() { return onchainSnapshotAt; }
    public void setOnchainSnapshotAt(LocalDateTime onchainSnapshotAt) { this.onchainSnapshotAt = onchainSnapshotAt; }
    public BigDecimal getDiscrepancyEur() { return discrepancyEur; }
    public void setDiscrepancyEur(BigDecimal discrepancyEur) { this.discrepancyEur = discrepancyEur; }
    public BigDecimal getDiscrepancyThreshold() { return discrepancyThreshold; }
    public int getAlertsGenerated() { return alertsGenerated; }
    public void setAlertsGenerated(int alertsGenerated) { this.alertsGenerated = alertsGenerated; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
