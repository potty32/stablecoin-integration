package de.atruvia.stablecoin.repository;

import de.atruvia.stablecoin.entity.StablecoinTransaction;
import de.atruvia.stablecoin.entity.TransactionStatus;
import de.atruvia.stablecoin.entity.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StablecoinTransactionRepository extends JpaRepository<StablecoinTransaction, UUID> {
    Optional<StablecoinTransaction> findByIdempotencyKey(String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM StablecoinTransaction t WHERE t.id = :id")
    Optional<StablecoinTransaction> findByIdWithLock(@Param("id") UUID id);
    Page<StablecoinTransaction> findByCustomerAccountId(UUID customerAccountId, Pageable pageable);
    Page<StablecoinTransaction> findByCustomerAccountIdAndStatus(UUID customerAccountId, TransactionStatus status, Pageable pageable);
    Page<StablecoinTransaction> findByStatus(TransactionStatus status, Pageable pageable);
    List<StablecoinTransaction> findByStatusAndCreatedAtBetween(TransactionStatus status, LocalDateTime from, LocalDateTime to);
    Optional<StablecoinTransaction> findTopByCustomerAccountIdAndTypeAndStatusOrderByCreatedAtDesc(
            UUID customerAccountId, TransactionType type, TransactionStatus status);
}
