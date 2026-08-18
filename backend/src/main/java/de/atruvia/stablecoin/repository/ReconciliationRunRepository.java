package de.atruvia.stablecoin.repository;

import de.atruvia.stablecoin.entity.ReconciliationRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface ReconciliationRunRepository extends JpaRepository<ReconciliationRun, UUID> {

    Optional<ReconciliationRun> findByRunDateAndTenantId(LocalDate runDate, String tenantId);

    @Query("SELECT COALESCE(SUM(t.grossDebit), SUM(t.amountFiat)) FROM StablecoinTransaction t " +
           "WHERE t.status = 'SETTLED' AND t.type = 'OUTBOUND' " +
           "AND DATE(t.settledAt) = :runDate AND t.tenantId = :tenantId")
    BigDecimal sumSettledOutboundByDate(@Param("runDate") LocalDate runDate,
                                        @Param("tenantId") String tenantId);

    @Query("SELECT COALESCE(SUM(t.amountFiat), 0) FROM StablecoinTransaction t " +
           "WHERE t.status = 'SETTLED' AND t.type = 'INBOUND' " +
           "AND DATE(t.settledAt) = :runDate AND t.tenantId = :tenantId")
    BigDecimal sumSettledInboundByDate(@Param("runDate") LocalDate runDate,
                                       @Param("tenantId") String tenantId);

    @Query("SELECT COUNT(t) FROM StablecoinTransaction t " +
           "WHERE t.status = 'SETTLED' AND t.type = 'OUTBOUND' " +
           "AND DATE(t.settledAt) = :runDate AND t.tenantId = :tenantId")
    int countSettledOutboundByDate(@Param("runDate") LocalDate runDate,
                                   @Param("tenantId") String tenantId);
}
