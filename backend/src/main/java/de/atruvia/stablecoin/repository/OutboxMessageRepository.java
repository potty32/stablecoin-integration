package de.atruvia.stablecoin.repository;

import de.atruvia.stablecoin.entity.OutboxMessage;
import de.atruvia.stablecoin.entity.OutboxStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxMessageRepository extends JpaRepository<OutboxMessage, UUID> {
    List<OutboxMessage> findByStatusOrderByCreatedAtAsc(OutboxStatus status, Pageable pageable);
    List<OutboxMessage> findByTransactionId(UUID transactionId);
    /** G-11: Findet PENDING-Nachrichten, die vor dem Schwellwert-Zeitpunkt erstellt wurden. */
    List<OutboxMessage> findByStatusAndCreatedAtBefore(OutboxStatus status, LocalDateTime before);
}
