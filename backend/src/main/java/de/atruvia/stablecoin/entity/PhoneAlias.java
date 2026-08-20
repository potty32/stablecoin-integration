package de.atruvia.stablecoin.entity;

import de.atruvia.stablecoin.config.TenantEntityListener;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "phone_alias")
@EntityListeners(TenantEntityListener.class)
public class PhoneAlias {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "phone_number_hash", unique = true, nullable = false, length = 64)
    private String phoneNumberHash;

    @Column(name = "wallet_address", nullable = false, length = 100)
    private String walletAddress;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_account_id", nullable = false)
    private CustomerAccount customerAccount;

    @Column(name = "verified_at", nullable = false)
    private LocalDateTime verifiedAt;

    @PrePersist
    protected void onCreate() {
        verifiedAt = LocalDateTime.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getPhoneNumberHash() { return phoneNumberHash; }
    public void setPhoneNumberHash(String phoneNumberHash) { this.phoneNumberHash = phoneNumberHash; }
    public String getWalletAddress() { return walletAddress; }
    public void setWalletAddress(String walletAddress) { this.walletAddress = walletAddress; }
    public CustomerAccount getCustomerAccount() { return customerAccount; }
    public void setCustomerAccount(CustomerAccount customerAccount) { this.customerAccount = customerAccount; }
    @Column(name = "phone_hash_algorithm", nullable = false, length = 30)
    private String phoneHashAlgorithm = "HMAC_SHA256_V1";

    // V23: tenant_id für RLS (S-07)
    @Column(name = "tenant_id", nullable = false, length = 50)
    private String tenantId;

    public LocalDateTime getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(LocalDateTime verifiedAt) { this.verifiedAt = verifiedAt; }
    public String getPhoneHashAlgorithm() { return phoneHashAlgorithm; }
    public void setPhoneHashAlgorithm(String v) { this.phoneHashAlgorithm = v; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
}
