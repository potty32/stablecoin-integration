package de.atruvia.stablecoin.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "institutional_address_book")
public class InstitutionalAddressBook {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "label", nullable = false, length = 100)
    private String label;

    @Column(name = "wallet_address", nullable = false, length = 100)
    private String walletAddress;

    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false, length = 10)
    private StablecoinCurrency currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_score", nullable = false, length = 10)
    private RiskScore riskScore;

    @Column(name = "verified_at", nullable = false)
    private LocalDateTime verifiedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private InstitutionalAddressStatus status;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (riskScore == null) riskScore = RiskScore.LOW;
        if (status == null) status = InstitutionalAddressStatus.ACTIVE;
        if (verifiedAt == null) verifiedAt = LocalDateTime.now();
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public UUID getId() { return id; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public String getWalletAddress() { return walletAddress; }
    public void setWalletAddress(String walletAddress) { this.walletAddress = walletAddress; }
    public StablecoinCurrency getCurrency() { return currency; }
    public void setCurrency(StablecoinCurrency currency) { this.currency = currency; }
    public RiskScore getRiskScore() { return riskScore; }
    public void setRiskScore(RiskScore riskScore) { this.riskScore = riskScore; }
    public LocalDateTime getVerifiedAt() { return verifiedAt; }
    public InstitutionalAddressStatus getStatus() { return status; }
    public void setStatus(InstitutionalAddressStatus status) { this.status = status; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
