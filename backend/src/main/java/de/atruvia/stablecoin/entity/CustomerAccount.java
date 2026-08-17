package de.atruvia.stablecoin.entity;

import de.atruvia.stablecoin.config.TenantEntityListener;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "customer_account")
@EntityListeners(TenantEntityListener.class)
public class CustomerAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "customer_id", nullable = false, length = 50)
    private String customerId;

    @Column(nullable = false, unique = true, length = 34)
    private String iban;

    @Column(name = "wallet_address", length = 100)
    private String walletAddress;

    @Enumerated(EnumType.STRING)
    @Column(name = "customer_type", nullable = false, length = 10)
    private CustomerType customerType;

    @Enumerated(EnumType.STRING)
    @Column(name = "kyc_tier", nullable = false, length = 10)
    private KycTier kycTier;

    @Column(name = "tx_limit_single", nullable = false, precision = 18, scale = 6)
    private BigDecimal txLimitSingle;

    @Column(name = "tx_limit_daily", nullable = false, precision = 18, scale = 6)
    private BigDecimal txLimitDaily;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private AccountStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "tenant_id", nullable = false, length = 50)
    private String tenantId;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) status = AccountStatus.ACTIVE;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }
    public String getIban() { return iban; }
    public void setIban(String iban) { this.iban = iban; }
    public String getWalletAddress() { return walletAddress; }
    public void setWalletAddress(String walletAddress) { this.walletAddress = walletAddress; }
    public CustomerType getCustomerType() { return customerType; }
    public void setCustomerType(CustomerType customerType) { this.customerType = customerType; }
    public KycTier getKycTier() { return kycTier; }
    public void setKycTier(KycTier kycTier) { this.kycTier = kycTier; }
    public BigDecimal getTxLimitSingle() { return txLimitSingle; }
    public void setTxLimitSingle(BigDecimal txLimitSingle) { this.txLimitSingle = txLimitSingle; }
    public BigDecimal getTxLimitDaily() { return txLimitDaily; }
    public void setTxLimitDaily(BigDecimal txLimitDaily) { this.txLimitDaily = txLimitDaily; }
    public AccountStatus getStatus() { return status; }
    public void setStatus(AccountStatus status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
}
