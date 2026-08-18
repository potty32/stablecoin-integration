package de.atruvia.stablecoin.service.b2b;

import de.atruvia.stablecoin.repository.StablecoinTransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * G-09: Nachtlicher Cleanup abgelaufener Idempotenz-Records.
 *
 * Nach 30 Tagen können Idempotenz-Keys wiederverwendet werden (PSD2-konform).
 * Nur Terminal-Status-Transaktionen werden gelöscht (SETTLED, FAILED, etc.).
 * Offene Transaktionen bleiben erhalten.
 */
@Service
public class IdempotencyCleanupService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyCleanupService.class);

    private final StablecoinTransactionRepository txRepository;

    public IdempotencyCleanupService(StablecoinTransactionRepository txRepository) {
        this.txRepository = txRepository;
    }

    @Scheduled(cron = "0 3 2 * * ?")  // täglich 02:03 Uhr
    @Transactional
    public void cleanupExpiredIdempotencyKeys() {
        LocalDateTime threshold = LocalDateTime.now();
        int deleted = txRepository.deleteExpiredIdempotencyKeys(threshold);
        if (deleted > 0) {
            log.info("[IDEM-CLEANUP] {} abgelaufene Idempotenz-Keys bereinigt", deleted);
        }
    }
}
