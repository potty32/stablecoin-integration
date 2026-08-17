package de.atruvia.stablecoin.service.compliance;

import de.atruvia.stablecoin.client.ChainalysisClient;
import de.atruvia.stablecoin.client.dto.AddressScreenRequestDto;
import de.atruvia.stablecoin.client.dto.AddressScreenResponseDto;
import de.atruvia.stablecoin.entity.AuditLog;
import de.atruvia.stablecoin.exception.ComplianceBlockException;
import de.atruvia.stablecoin.repository.AuditLogRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ComplianceService {

    private static final Logger log = LoggerFactory.getLogger(ComplianceService.class);

    private final ChainalysisClient chainalysisClient;
    private final AuditLogRepository auditLogRepository;

    public ComplianceService(ChainalysisClient chainalysisClient, AuditLogRepository auditLogRepository) {
        this.chainalysisClient = chainalysisClient;
        this.auditLogRepository = auditLogRepository;
    }

    @CircuitBreaker(name = "chainalysis", fallbackMethod = "screenAddressFallback")
    @Transactional
    public void screenAndAssert(String walletAddress, UUID transactionId, String userId) {
        AddressScreenResponseDto result = chainalysisClient.screenAddress(
                new AddressScreenRequestDto(walletAddress, "USDC", "POLYGON", "outgoing")
        );

        AuditLog entry = new AuditLog();
        entry.setEntityType("StablecoinTransaction");
        entry.setEntityId(transactionId);
        entry.setTransactionId(transactionId);
        entry.setAction("COMPLIANCE_SCREEN");
        entry.setUserId(userId);
        entry.setDetails("AML-Screening: walletAddress=" + walletAddress
                + ", riskScore=" + result.riskScore()
                + ", sanctioned=" + result.sanctionedEntity()
                + ", approved=" + result.approved());
        auditLogRepository.save(entry);

        if (!result.approved()) {
            log.error("[COMPLIANCE BLOCK] tx={} address={} riskScore={} categories={}",
                    transactionId, walletAddress, result.riskScore(), result.riskCategories());

            AuditLog blockEntry = new AuditLog();
            blockEntry.setEntityType("StablecoinTransaction");
            blockEntry.setEntityId(transactionId);
            blockEntry.setTransactionId(transactionId);
            blockEntry.setAction("COMPLIANCE_BLOCKED");
            blockEntry.setUserId(userId);
            blockEntry.setDetails("AML-Block: walletAddress=" + walletAddress
                    + ", riskScore=" + result.riskScore()
                    + ", categories=" + result.riskCategories());
            auditLogRepository.save(blockEntry);

            throw new ComplianceBlockException(walletAddress, result.riskScore());
        }

        log.info("[COMPLIANCE OK] tx={} address={} riskScore={}", transactionId, walletAddress, result.riskScore());
    }

    private void screenAddressFallback(String walletAddress, UUID transactionId, String userId, Throwable ex) {
        if (ex instanceof ComplianceBlockException cbe) {
            throw cbe;
        }
        log.warn("[COMPLIANCE FALLBACK] Chainalysis unavailable for tx={} address={} — blocking as precaution",
                transactionId, walletAddress);

        AuditLog entry = new AuditLog();
        entry.setEntityType("StablecoinTransaction");
        entry.setEntityId(transactionId);
        entry.setTransactionId(transactionId);
        entry.setAction("COMPLIANCE_FALLBACK_BLOCK");
        entry.setUserId(userId);
        entry.setDetails("AML-Block (Chainalysis nicht erreichbar): walletAddress=" + walletAddress);
        auditLogRepository.save(entry);

        throw new ComplianceBlockException(walletAddress, "UNAVAILABLE");
    }
}
