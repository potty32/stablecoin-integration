package de.atruvia.stablecoin.repository;

import de.atruvia.stablecoin.entity.OutboxMessage;
import de.atruvia.stablecoin.entity.OutboxStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.stereotype.Repository;

import jakarta.persistence.QueryHint;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxMessageRepository extends JpaRepository<OutboxMessage, UUID> {

    /** T-03-Fix: SKIP LOCKED verhindert Doppelverarbeitung in Cluster-Deployments. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("SELECT m FROM OutboxMessage m WHERE m.status = :status ORDER BY m.createdAt ASC")
    List<OutboxMessage> findByStatusOrderByCreatedAtAsc(OutboxStatus status, Pageable pageable);
    List<OutboxMessage> findByTransactionId(UUID transactionId);
    /** G-11: Findet PENDING-Nachrichten, die vor dem Schwellwert-Zeitpunkt erstellt wurden. */
    List<OutboxMessage> findByStatusAndCreatedAtBefore(OutboxStatus status, LocalDateTime before);
}
