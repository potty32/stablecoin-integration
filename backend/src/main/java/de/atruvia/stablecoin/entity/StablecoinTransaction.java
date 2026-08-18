package de.atruvia.stablecoin.entity;

import de.atruvia.stablecoin.config.TenantEntityListener;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "stablecoin_transaction")
@EntityListeners(TenantEntityListener.class)
public class StablecoinTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 100)
    private String idempotencyKey;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "customer_account_id", nullable = false)
    private CustomerAccount customerAccount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private StablecoinCurrency currency;

    @Column(name = "amount_fiat", nullable = false, precision = 18, scale = 6)
    private BigDecimal amountFiat;

    @Column(name = "amount_stablecoin", precision = 18, scale = 6)
    private BigDecimal amountStablecoin;

    @Column(name = "fx_rate", precision = 18, scale = 8)
    private BigDecimal fxRate;

    @Column(name = "fx_spread", precision = 8, scale = 6)
    private BigDecimal fxSpread;

    @Column(name = "transaction_fee", precision = 18, scale = 6)
    private BigDecimal transactionFee;

    @Column(name = "gas_cost", precision = 18, scale = 8)
    private BigDecimal gasCost;

    @Column(name = "gross_revenue", precision = 18, scale = 6)
    private BigDecimal grossRevenue;

    @Column(name = "source_wallet", length = 100)
    private String sourceWallet;

    @Column(name = "destination_wallet", nullable = false, length = 100)
    private String destinationWallet;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rate_quote_id")
    private RateQuote rateQuote;

    @Column(name = "circle_transaction_id", length = 100)
    private String circleTransactionId;

    @Column(name = "blockchain_hash", length = 100)
    private String blockchainHash;

    @Column(name = "hold_id", length = 100)
    private String holdId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    private TransactionStatus status;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "settled_at")
    private LocalDateTime settledAt;

    @Column(name = "tenant_id", nullable = false, length = 50)
    private String tenantId;

    @Column(name = "parent_transaction_id")
    private UUID parentTransactionId;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) status = TransactionStatus.CREATED;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public CustomerAccount getCustomerAccount() { return customerAccount; }
    public void setCustomerAccount(CustomerAccount customerAccount) { this.customerAccount = customerAccount; }
    public TransactionType getType() { return type; }
    public void setType(TransactionType type) { this.type = type; }
    public StablecoinCurrency getCurrency() { return currency; }
    public void setCurrency(StablecoinCurrency currency) { this.currency = currency; }
    public BigDecimal getAmountFiat() { return amountFiat; }
    public void setAmountFiat(BigDecimal amountFiat) { this.amountFiat = amountFiat; }
    public BigDecimal getAmountStablecoin() { return amountStablecoin; }
    public void setAmountStablecoin(BigDecimal amountStablecoin) { this.amountStablecoin = amountStablecoin; }
    public BigDecimal getFxRate() { return fxRate; }
    public void setFxRate(BigDecimal fxRate) { this.fxRate = fxRate; }
    public BigDecimal getFxSpread() { return fxSpread; }
    public void setFxSpread(BigDecimal fxSpread) { this.fxSpread = fxSpread; }
    public BigDecimal getTransactionFee() { return transactionFee; }
    public void setTransactionFee(BigDecimal transactionFee) { this.transactionFee = transactionFee; }
    public BigDecimal getGasCost() { return gasCost; }
    public void setGasCost(BigDecimal gasCost) { this.gasCost = gasCost; }
    public BigDecimal getGrossRevenue() { return grossRevenue; }
    public void setGrossRevenue(BigDecimal grossRevenue) { this.grossRevenue = grossRevenue; }
    public String getSourceWallet() { return sourceWallet; }
    public void setSourceWallet(String sourceWallet) { this.sourceWallet = sourceWallet; }
    public String getDestinationWallet() { return destinationWallet; }
    public void setDestinationWallet(String destinationWallet) { this.destinationWallet = destinationWallet; }
    public RateQuote getRateQuote() { return rateQuote; }
    public void setRateQuote(RateQuote rateQuote) { this.rateQuote = rateQuote; }
    public String getCircleTransactionId() { return circleTransactionId; }
    public void setCircleTransactionId(String circleTransactionId) { this.circleTransactionId = circleTransactionId; }
    public String getBlockchainHash() { return blockchainHash; }
    public void setBlockchainHash(String blockchainHash) { this.blockchainHash = blockchainHash; }
    public String getHoldId() { return holdId; }
    public void setHoldId(String holdId) { this.holdId = holdId; }
    public TransactionStatus getStatus() { return status; }
    public void setStatus(TransactionStatus status) { this.status = status; }
    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public LocalDateTime getSettledAt() { return settledAt; }
    public void setSettledAt(LocalDateTime settledAt) { this.settledAt = settledAt; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public UUID getParentTransactionId() { return parentTransactionId; }
    public void setParentTransactionId(UUID parentTransactionId) { this.parentTransactionId = parentTransactionId; }

    // ── G-01: Buchungskreislauf (V11) ─────────────────────────────────────────

    @Column(name = "gross_debit", precision = 18, scale = 6)
    private BigDecimal grossDebit;

    @Column(name = "fee_amount", precision = 18, scale = 6)
    private BigDecimal feeAmount;

    @Column(name = "ledger_booking_reference", length = 100)
    private String ledgerBookingReference;

    @Column(name = "slippage_tolerance_bps")
    private Integer slippageToleranceBps;

    // ── G-02: Quellensteuer-Nachweis (V11) ────────────────────────────────────

    @Column(name = "tax_withheld", precision = 18, scale = 6)
    private BigDecimal taxWithheld;

    public BigDecimal getGrossDebit() { return grossDebit; }
    public void setGrossDebit(BigDecimal grossDebit) { this.grossDebit = grossDebit; }
    public BigDecimal getFeeAmount() { return feeAmount; }
    public void setFeeAmount(BigDecimal feeAmount) { this.feeAmount = feeAmount; }
    public String getLedgerBookingReference() { return ledgerBookingReference; }
    public void setLedgerBookingReference(String ledgerBookingReference) { this.ledgerBookingReference = ledgerBookingReference; }
    public Integer getSlippageToleranceBps() { return slippageToleranceBps; }
    public void setSlippageToleranceBps(Integer slippageToleranceBps) { this.slippageToleranceBps = slippageToleranceBps; }
    public BigDecimal getTaxWithheld() { return taxWithheld; }
    public void setTaxWithheld(BigDecimal taxWithheld) { this.taxWithheld = taxWithheld; }
}
