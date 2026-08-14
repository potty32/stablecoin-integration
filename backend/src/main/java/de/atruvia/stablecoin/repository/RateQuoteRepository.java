package de.atruvia.stablecoin.repository;

import de.atruvia.stablecoin.entity.QuoteStatus;
import de.atruvia.stablecoin.entity.RateQuote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RateQuoteRepository extends JpaRepository<RateQuote, UUID> {
    Optional<RateQuote> findByIdAndStatus(UUID id, QuoteStatus status);
}
