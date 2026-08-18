package de.atruvia.stablecoin.repository;

import de.atruvia.stablecoin.entity.TaxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface TaxEventRepository extends JpaRepository<TaxEvent, UUID> {}
