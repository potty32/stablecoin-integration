package de.atruvia.stablecoin.repository;

import de.atruvia.stablecoin.entity.YieldPosition;
import de.atruvia.stablecoin.entity.YieldStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface YieldPositionRepository extends JpaRepository<YieldPosition, UUID> {
    Optional<YieldPosition> findByCustomerAccountIdAndStatus(UUID customerAccountId, YieldStatus status);
}
