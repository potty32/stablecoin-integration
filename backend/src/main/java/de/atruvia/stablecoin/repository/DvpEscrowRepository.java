package de.atruvia.stablecoin.repository;

import de.atruvia.stablecoin.entity.DvpEscrow;
import de.atruvia.stablecoin.entity.DvpEscrowStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface DvpEscrowRepository extends JpaRepository<DvpEscrow, UUID> {

    Optional<DvpEscrow> findByEscrowReference(String escrowReference);

    @Query("SELECT e FROM DvpEscrow e WHERE e.id = :id AND e.escrowReference = :ref")
    Optional<DvpEscrow> findByIdAndEscrowReference(@Param("id") UUID id, @Param("ref") String escrowReference);

    long countByStatus(DvpEscrowStatus status);
}
