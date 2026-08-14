package de.atruvia.stablecoin.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "address_book",
       uniqueConstraints = @UniqueConstraint(columnNames = {"customer_account_id", "wallet_address"}))
public class AddressBook {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_account_id", nullable = false)
    private CustomerAccount customerAccount;

    @Column(nullable = false, length = 100)
    private String label;

    @Column(name = "wallet_address", nullable = false, length = 100)
    private String walletAddress;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private StablecoinCurrency currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_score", nullable = false, length = 10)
    private RiskScore riskScore;

    @Column(name = "verified_at", nullable = false)
    private LocalDateTime verifiedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AddressStatus status;

    @PrePersist
    protected void onCreate() {
        verifiedAt = LocalDateTime.now();
        if (riskScore == null) riskScore = RiskScore.LOW;
        if (status == null) status = AddressStatus.ACTIVE;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public CustomerAccount getCustomerAccount() { return customerAccount; }
    public void setCustomerAccount(CustomerAccount customerAccount) { this.customerAccount = customerAccount; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public String getWalletAddress() { return walletAddress; }
    public void setWalletAddress(String walletAddress) { this.walletAddress = walletAddress; }
    public StablecoinCurrency getCurrency() { return currency; }
    public void setCurrency(StablecoinCurrency currency) { this.currency = currency; }
    public RiskScore getRiskScore() { return riskScore; }
    public void setRiskScore(RiskScore riskScore) { this.riskScore = riskScore; }
    public LocalDateTime getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(LocalDateTime verifiedAt) { this.verifiedAt = verifiedAt; }
    public AddressStatus getStatus() { return status; }
    public void setStatus(AddressStatus status) { this.status = status; }
}
