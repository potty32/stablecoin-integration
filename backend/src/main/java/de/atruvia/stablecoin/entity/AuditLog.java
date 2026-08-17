package de.atruvia.stablecoin.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @Column(nullable = false, length = 50)
    private String action;

    @Column(name = "user_id", length = 100)
    private String userId;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @Column(name = "transaction_id")
    private UUID transactionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 25)
    private TransactionStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", length = 25)
    private TransactionStatus toStatus;

    @Column(name = "details", columnDefinition = "TEXT")
    private String details;

    @PrePersist
    protected void onCreate() {
        timestamp = LocalDateTime.now();
    }

    public UUID getId() { return id; }
    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }
    public UUID getEntityId() { return entityId; }
    public void setEntityId(UUID entityId) { this.entityId = entityId; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public UUID getTransactionId() { return transactionId; }
    public void setTransactionId(UUID transactionId) { this.transactionId = transactionId; }
    public TransactionStatus getFromStatus() { return fromStatus; }
    public void setFromStatus(TransactionStatus fromStatus) { this.fromStatus = fromStatus; }
    public TransactionStatus getToStatus() { return toStatus; }
    public void setToStatus(TransactionStatus toStatus) { this.toStatus = toStatus; }
    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }
}
