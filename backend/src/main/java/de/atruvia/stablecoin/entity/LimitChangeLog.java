package de.atruvia.stablecoin.entity;

import de.atruvia.stablecoin.config.TenantEntityListener;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * G-08: Audit-Trail für manuelle Limit-Änderungen auf Kundenkonto-Ebene.
 * Dokumentiert wer wann welches Limit geändert hat und warum.
 */
@Entity
@Table(name = "limit_change_log")
@EntityListeners(TenantEntityListener.class)
public class LimitChangeLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "customer_account_id", nullable = false)
    private UUID customerAccountId;

    @Column(name = "changed_by", nullable = false, length = 100)
    private String changedBy;

    @Column(name = "field_name", nullable = false, length = 50)
    private String fieldName;  // TX_LIMIT_SINGLE | TX_LIMIT_DAILY

    @Column(name = "old_value", precision = 18, scale = 6)
    private BigDecimal oldValue;

    @Column(name = "new_value", precision = 18, scale = 6)
    private BigDecimal newValue;

    @Column(name = "reason")
    private String reason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "tenant_id", nullable = false, length = 50)
    private String tenantId;

    public UUID getId() { return id; }
    public UUID getCustomerAccountId() { return customerAccountId; }
    public void setCustomerAccountId(UUID v) { this.customerAccountId = v; }
    public String getChangedBy() { return changedBy; }
    public void setChangedBy(String v) { this.changedBy = v; }
    public String getFieldName() { return fieldName; }
    public void setFieldName(String v) { this.fieldName = v; }
    public BigDecimal getOldValue() { return oldValue; }
    public void setOldValue(BigDecimal v) { this.oldValue = v; }
    public BigDecimal getNewValue() { return newValue; }
    public void setNewValue(BigDecimal v) { this.newValue = v; }
    public String getReason() { return reason; }
    public void setReason(String v) { this.reason = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
}
