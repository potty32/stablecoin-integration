package de.atruvia.stablecoin.entity;

import de.atruvia.stablecoin.config.TenantEntityListener;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DvP-Escrow-Entität für atomare Delivery-versus-Payment-Transaktionen.
 *
 * Eliminiert das Herstatt-Risiko bei tokenisierten Wertpapiergeschäften:
 * Stablecoins werden in einem Sperrzustand gehalten bis die Wertpapierseite
 * (Deka, Union Investment, Clearstream) die Übertragung bestätigt.
 *
 * Zustandsübergänge: ESCROWED → SETTLED | ESCROWED → CANCELLED
 */
@Entity
@Table(name = "dvp_escrow", indexes = {
        @Index(name = "idx_dvp_escrow_reference", columnList = "escrow_reference"),
        @Index(name = "idx_dvp_escrow_tenant",    columnList = "tenant_id"),
        @Index(name = "idx_dvp_escrow_customer",  columnList = "customer_account_id, status")
})
@EntityListeners(TenantEntityListener.class)
public class DvpEscrow {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "customer_account_id", nullable = false)
    private CustomerAccount customerAccount;

    @Column(name = "amount_fiat", nullable = false, precision = 18, scale = 6)
    private BigDecimal amountFiat;

    @Column(name = "amount_stablecoin", nullable = false, precision = 18, scale = 6)
    private BigDecimal amountStablecoin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private StablecoinCurrency currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DvpEscrowStatus status;

    @Column(name = "settlement_wallet", nullable = false, length = 100)
    private String settlementWallet;

    @Column(name = "securities_isin", nullable = false, length = 20)
    private String securitiesIsin;

    @Column(name = "securities_amount", nullable = false, precision = 18, scale = 6)
    private BigDecimal securitiesAmount;

    @Column(name = "escrow_reference", nullable = false, unique = true, length = 100)
    private String escrowReference;

    @Column(name = "securities_system_id", length = 50)
    private String securitiesSystemId;

    @Column(name = "hold_id", length = 100)
    private String holdId;

    @Column(name = "fee_amount", precision = 18, scale = 6)
    private BigDecimal feeAmount;

    @Column(name = "blockchain_hash", length = 100)
    private String blockchainHash;

    @Column(name = "locked_at", nullable = false)
    private LocalDateTime lockedAt;

    @Column(name = "settled_at")
    private LocalDateTime settledAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "cancellation_reason", columnDefinition = "TEXT")
    private String cancellationReason;

    @Column(name = "tenant_id", nullable = false, length = 50)
    private String tenantId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (lockedAt == null) lockedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // ── Getters & Setters ──────────────────────────────────────────────────────

    public UUID getId() { return id; }
    public CustomerAccount getCustomerAccount() { return customerAccount; }
    public void setCustomerAccount(CustomerAccount customerAccount) { this.customerAccount = customerAccount; }
    public BigDecimal getAmountFiat() { return amountFiat; }
    public void setAmountFiat(BigDecimal amountFiat) { this.amountFiat = amountFiat; }
    public BigDecimal getAmountStablecoin() { return amountStablecoin; }
    public void setAmountStablecoin(BigDecimal amountStablecoin) { this.amountStablecoin = amountStablecoin; }
    public StablecoinCurrency getCurrency() { return currency; }
    public void setCurrency(StablecoinCurrency currency) { this.currency = currency; }
    public DvpEscrowStatus getStatus() { return status; }
    public void setStatus(DvpEscrowStatus status) { this.status = status; }
    public String getSettlementWallet() { return settlementWallet; }
    public void setSettlementWallet(String settlementWallet) { this.settlementWallet = settlementWallet; }
    public String getSecuritiesIsin() { return securitiesIsin; }
    public void setSecuritiesIsin(String securitiesIsin) { this.securitiesIsin = securitiesIsin; }
    public BigDecimal getSecuritiesAmount() { return securitiesAmount; }
    public void setSecuritiesAmount(BigDecimal securitiesAmount) { this.securitiesAmount = securitiesAmount; }
    public String getEscrowReference() { return escrowReference; }
    public void setEscrowReference(String escrowReference) { this.escrowReference = escrowReference; }
    public String getSecuritiesSystemId() { return securitiesSystemId; }
    public void setSecuritiesSystemId(String securitiesSystemId) { this.securitiesSystemId = securitiesSystemId; }
    public String getHoldId() { return holdId; }
    public void setHoldId(String holdId) { this.holdId = holdId; }
    public BigDecimal getFeeAmount() { return feeAmount; }
    public void setFeeAmount(BigDecimal feeAmount) { this.feeAmount = feeAmount; }
    public String getBlockchainHash() { return blockchainHash; }
    public void setBlockchainHash(String blockchainHash) { this.blockchainHash = blockchainHash; }
    public LocalDateTime getLockedAt() { return lockedAt; }
    public void setLockedAt(LocalDateTime lockedAt) { this.lockedAt = lockedAt; }
    public LocalDateTime getSettledAt() { return settledAt; }
    public void setSettledAt(LocalDateTime settledAt) { this.settledAt = settledAt; }
    public LocalDateTime getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(LocalDateTime cancelledAt) { this.cancelledAt = cancelledAt; }
    public String getCancellationReason() { return cancellationReason; }
    public void setCancellationReason(String cancellationReason) { this.cancellationReason = cancellationReason; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
