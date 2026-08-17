package de.atruvia.stablecoin.entity;

import de.atruvia.stablecoin.config.TenantEntityListener;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "yield_position")
@EntityListeners(TenantEntityListener.class)
public class YieldPosition {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_account_id", nullable = false)
    private CustomerAccount customerAccount;

    @Column(name = "principal", nullable = false, precision = 18, scale = 6)
    private BigDecimal principal;

    @Column(name = "interest_rate", nullable = false, precision = 10, scale = 6)
    private BigDecimal interestRate;

    @Column(name = "deposited_at", nullable = false)
    private LocalDateTime depositedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private YieldStatus status;

    @Column(name = "deposit_transaction_id")
    private UUID depositTransactionId;

    @Column(name = "tenant_id", nullable = false, length = 50)
    private String tenantId;

    @PrePersist
    void prePersist() {
        if (status == null) status = YieldStatus.ACTIVE;
        if (interestRate == null) interestRate = new BigDecimal("0.035");
        if (depositedAt == null) depositedAt = LocalDateTime.now();
    }

    public UUID getId() { return id; }
    public CustomerAccount getCustomerAccount() { return customerAccount; }
    public void setCustomerAccount(CustomerAccount customerAccount) { this.customerAccount = customerAccount; }
    public BigDecimal getPrincipal() { return principal; }
    public void setPrincipal(BigDecimal principal) { this.principal = principal; }
    public BigDecimal getInterestRate() { return interestRate; }
    public void setInterestRate(BigDecimal interestRate) { this.interestRate = interestRate; }
    public LocalDateTime getDepositedAt() { return depositedAt; }
    public void setDepositedAt(LocalDateTime depositedAt) { this.depositedAt = depositedAt; }
    public LocalDateTime getClosedAt() { return closedAt; }
    public void setClosedAt(LocalDateTime closedAt) { this.closedAt = closedAt; }
    public YieldStatus getStatus() { return status; }
    public void setStatus(YieldStatus status) { this.status = status; }
    public UUID getDepositTransactionId() { return depositTransactionId; }
    public void setDepositTransactionId(UUID depositTransactionId) { this.depositTransactionId = depositTransactionId; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
}
