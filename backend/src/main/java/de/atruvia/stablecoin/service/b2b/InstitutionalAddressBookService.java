package de.atruvia.stablecoin.service.b2b;

import de.atruvia.stablecoin.client.ChainalysisClient;
import de.atruvia.stablecoin.client.dto.AddressScreenRequestDto;
import de.atruvia.stablecoin.client.dto.AddressScreenResponseDto;
import de.atruvia.stablecoin.dto.request.b2b.AddInstitutionalAddressRequest;
import de.atruvia.stablecoin.dto.response.InstitutionalAddressBookResponse;
import de.atruvia.stablecoin.entity.AuditLog;
import de.atruvia.stablecoin.entity.InstitutionalAddressBook;
import de.atruvia.stablecoin.entity.InstitutionalAddressStatus;
import de.atruvia.stablecoin.entity.RiskScore;
import de.atruvia.stablecoin.exception.ComplianceBlockException;
import de.atruvia.stablecoin.repository.AuditLogRepository;
import de.atruvia.stablecoin.repository.InstitutionalAddressBookRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class InstitutionalAddressBookService {

    private static final Logger log = LoggerFactory.getLogger(InstitutionalAddressBookService.class);

    private final InstitutionalAddressBookRepository repository;
    private final ChainalysisClient chainalysisClient;
    private final AuditLogRepository auditLogRepository;

    public InstitutionalAddressBookService(
            InstitutionalAddressBookRepository repository,
            ChainalysisClient chainalysisClient,
            AuditLogRepository auditLogRepository) {
        this.repository = repository;
        this.chainalysisClient = chainalysisClient;
        this.auditLogRepository = auditLogRepository;
    }

    @CircuitBreaker(name = "chainalysis", fallbackMethod = "addAddressFallback")
    @Transactional
    public InstitutionalAddressBookResponse addAddress(AddInstitutionalAddressRequest request, String userId) {
        AddressScreenResponseDto screening = chainalysisClient.screenAddress(
                new AddressScreenRequestDto(request.walletAddress(), request.currency().name(), "POLYGON", "outgoing"));

        if (!screening.approved()) {
            log.warn("[INST-ADDR] Blocked high-risk address={} riskScore={}", request.walletAddress(), screening.riskScore());
            writeAuditLog(UUID.randomUUID(), "INST_ADDRESS_SCREENING_BLOCKED", userId,
                    String.format("{\"address\":\"%s\",\"riskScore\":\"%s\"}", request.walletAddress(), screening.riskScore()));
            throw new ComplianceBlockException(request.walletAddress(), screening.riskScore());
        }

        InstitutionalAddressBook entry = new InstitutionalAddressBook();
        entry.setLabel(request.label());
        entry.setWalletAddress(request.walletAddress());
        entry.setCurrency(request.currency());
        entry.setRiskScore(RiskScore.valueOf(screening.riskScore()));
        entry.setCreatedBy(userId);
        InstitutionalAddressBook saved = repository.save(entry);

        writeAuditLog(saved.getId(), "INST_ADDRESS_ADDED", userId,
                String.format("{\"address\":\"%s\",\"label\":\"%s\",\"riskScore\":\"%s\"}",
                        saved.getWalletAddress(), saved.getLabel(), saved.getRiskScore()));

        log.info("[INST-ADDR] Added address={} riskScore={} by={}", saved.getWalletAddress(), saved.getRiskScore(), userId);
        return toResponse(saved);
    }

    private InstitutionalAddressBookResponse addAddressFallback(
            AddInstitutionalAddressRequest request, String userId, Throwable ex) {
        if (ex instanceof ComplianceBlockException cbe) throw cbe;
        log.error("[INST-ADDR] Chainalysis unavailable — blocking address={} as precaution", request.walletAddress());
        throw new ComplianceBlockException(request.walletAddress(), "UNAVAILABLE");
    }

    @Transactional(readOnly = true)
    public List<InstitutionalAddressBookResponse> listAddresses() {
        return repository.findByStatus(InstitutionalAddressStatus.ACTIVE)
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public void revokeAddress(UUID id, String userId) {
        InstitutionalAddressBook address = repository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Institutional address not found: " + id));
        address.setStatus(InstitutionalAddressStatus.REVOKED);
        repository.save(address);
        writeAuditLog(id, "INST_ADDRESS_REVOKED", userId,
                String.format("{\"address\":\"%s\",\"label\":\"%s\"}", address.getWalletAddress(), address.getLabel()));
        log.info("[INST-ADDR] Revoked address={} by={}", address.getWalletAddress(), userId);
    }

    private void writeAuditLog(UUID entityId, String action, String userId, String newState) {
        AuditLog entry = new AuditLog();
        entry.setEntityType("InstitutionalAddressBook");
        entry.setEntityId(entityId);
        entry.setAction(action);
        entry.setUserId(userId);
        entry.setNewState(newState);
        auditLogRepository.save(entry);
    }

    private InstitutionalAddressBookResponse toResponse(InstitutionalAddressBook a) {
        return new InstitutionalAddressBookResponse(
                a.getId(), a.getLabel(), a.getWalletAddress(),
                a.getCurrency(), a.getRiskScore(), a.getStatus(),
                a.getCreatedBy(), a.getVerifiedAt());
    }
}
