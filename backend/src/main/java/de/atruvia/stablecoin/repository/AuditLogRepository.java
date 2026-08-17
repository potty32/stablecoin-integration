package de.atruvia.stablecoin.repository;

import de.atruvia.stablecoin.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
    List<AuditLog> findByTransactionIdOrderByTimestampAsc(UUID transactionId);
    List<AuditLog> findByEntityTypeAndEntityIdOrderByTimestampAsc(String entityType, UUID entityId);
}
