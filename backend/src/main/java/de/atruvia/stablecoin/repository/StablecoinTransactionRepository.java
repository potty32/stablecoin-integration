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
import java.math.BigDecimal;
import java.util.Collection;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StablecoinTransactionRepository extends JpaRepository<StablecoinTransaction, UUID> {
    Optional<StablecoinTransaction> findByIdempotencyKey(String idempotencyKey);

    /** G-09: Idempotenz-Check nur für nicht-abgelaufene Einträge. */
    @Query("SELECT t FROM StablecoinTransaction t WHERE t.idempotencyKey = :key " +
           "AND (t.idempotencyExpiresAt IS NULL OR t.idempotencyExpiresAt > :now)")
    Optional<StablecoinTransaction> findByIdempotencyKeyAndNotExpired(
            @Param("key") String key, @Param("now") java.time.LocalDateTime now);

    /** G-09: Löscht abgelaufene Idempotenz-Records (nur Terminal-Status-TXs). */
    @org.springframework.data.jpa.repository.Modifying
    @Query("DELETE FROM StablecoinTransaction t WHERE t.idempotencyExpiresAt < :threshold " +
           "AND t.status IN ('SETTLED','FAILED','REJECTED','EXPIRED','REDEEMED','RETURNED')")
    int deleteExpiredIdempotencyKeys(@Param("threshold") java.time.LocalDateTime threshold);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM StablecoinTransaction t WHERE t.id = :id")
    Optional<StablecoinTransaction> findByIdWithLock(@Param("id") UUID id);
    Page<StablecoinTransaction> findByCustomerAccountId(UUID customerAccountId, Pageable pageable);
    Page<StablecoinTransaction> findByCustomerAccountIdAndStatus(UUID customerAccountId, TransactionStatus status, Pageable pageable);
    Page<StablecoinTransaction> findByStatus(TransactionStatus status, Pageable pageable);
    List<StablecoinTransaction> findByStatusAndCreatedAtBetween(TransactionStatus status, LocalDateTime from, LocalDateTime to);
    Optional<StablecoinTransaction> findTopByCustomerAccountIdAndTypeAndStatusOrderByCreatedAtDesc(
            UUID customerAccountId, TransactionType type, TransactionStatus status);

    Optional<StablecoinTransaction> findByCircleTransactionId(String circleTransactionId);
    Optional<StablecoinTransaction> findByBlockchainHash(String blockchainHash);

    List<StablecoinTransaction> findByStatusIn(Collection<TransactionStatus> statuses);

    /**
     * G-08/F-02: Tages-Aggregat für Daily-Limit-Prüfung (GwG §3, PSD2).
     * Zählt alle nicht-terminal-fehlgeschlagenen OUTBOUND-Transaktionen des Tages.
     * Terminal-Failed: FAILED, REJECTED, EXPIRED, RETURNED → nicht im Limit.
     */
    @Query("""
           SELECT COALESCE(SUM(t.amountFiat), 0) FROM StablecoinTransaction t
           WHERE t.customerAccount.id = :accountId
             AND t.type = de.atruvia.stablecoin.entity.TransactionType.OUTBOUND
             AND t.createdAt >= :startOfDay
             AND t.status NOT IN (
                 de.atruvia.stablecoin.entity.TransactionStatus.FAILED,
                 de.atruvia.stablecoin.entity.TransactionStatus.REJECTED,
                 de.atruvia.stablecoin.entity.TransactionStatus.EXPIRED,
                 de.atruvia.stablecoin.entity.TransactionStatus.RETURNED
             )
           """)
    BigDecimal sumOutboundAmountToday(@Param("accountId") UUID accountId,
                                      @Param("startOfDay") LocalDateTime startOfDay);
}
