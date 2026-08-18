package de.atruvia.stablecoin.repository;

import de.atruvia.stablecoin.entity.YieldPosition;
import de.atruvia.stablecoin.entity.YieldStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface YieldPositionRepository extends JpaRepository<YieldPosition, UUID> {
    Optional<YieldPosition> findByCustomerAccountIdAndStatus(UUID customerAccountId, YieldStatus status);

    /** G-15: ACTIVE Positionen, die in diesem Jahr noch nicht bewertet wurden. */
    @Query("SELECT yp FROM YieldPosition yp WHERE yp.status = de.atruvia.stablecoin.entity.YieldStatus.ACTIVE " +
           "AND (yp.lastValuedYear IS NULL OR yp.lastValuedYear <> :year)")
    List<YieldPosition> findActiveNotValuedInYear(@Param("year") int year);
}
