package de.atruvia.stablecoin.repository;

import de.atruvia.stablecoin.entity.ApprovalStatus;
import de.atruvia.stablecoin.entity.ApprovalWorkflow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApprovalWorkflowRepository extends JpaRepository<ApprovalWorkflow, UUID> {
    Optional<ApprovalWorkflow> findByTransactionId(UUID transactionId);
    List<ApprovalWorkflow> findByStatusAndExpiresAtBefore(ApprovalStatus status, LocalDateTime now);
}
