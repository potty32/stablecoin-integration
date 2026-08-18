package de.atruvia.stablecoin.repository;

import de.atruvia.stablecoin.entity.LimitChangeLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface LimitChangeLogRepository extends JpaRepository<LimitChangeLog, UUID> {}
