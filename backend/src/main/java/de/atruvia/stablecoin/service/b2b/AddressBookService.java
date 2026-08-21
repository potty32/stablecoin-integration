package de.atruvia.stablecoin.service.b2b;

import de.atruvia.stablecoin.client.ChainalysisClient;
import de.atruvia.stablecoin.client.dto.AddressScreenRequestDto;
import de.atruvia.stablecoin.client.dto.AddressScreenResponseDto;
import de.atruvia.stablecoin.dto.request.b2b.AddAddressRequest;
import de.atruvia.stablecoin.dto.response.AddressBookResponse;
import de.atruvia.stablecoin.entity.AddressBook;
import de.atruvia.stablecoin.entity.AddressStatus;
import de.atruvia.stablecoin.entity.AuditLog;
import de.atruvia.stablecoin.entity.CustomerAccount;
import de.atruvia.stablecoin.entity.RiskScore;
import de.atruvia.stablecoin.exception.ComplianceBlockException;
import de.atruvia.stablecoin.repository.AddressBookRepository;
import de.atruvia.stablecoin.repository.AuditLogRepository;
import de.atruvia.stablecoin.config.TenantContext;
import de.atruvia.stablecoin.repository.CustomerAccountRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class AddressBookService {

    private static final Logger log = LoggerFactory.getLogger(AddressBookService.class);

    private final AddressBookRepository addressBookRepository;
    private final CustomerAccountRepository accountRepository;
    private final ChainalysisClient chainalysisClient;
    private final AuditLogRepository auditLogRepository;

    public AddressBookService(AddressBookRepository addressBookRepository,
                              CustomerAccountRepository accountRepository,
                              ChainalysisClient chainalysisClient,
                              AuditLogRepository auditLogRepository) {
        this.addressBookRepository = addressBookRepository;
        this.accountRepository = accountRepository;
        this.chainalysisClient = chainalysisClient;
        this.auditLogRepository = auditLogRepository;
    }

    @CircuitBreaker(name = "chainalysis", fallbackMethod = "addAddressFallback")
    @Transactional
    public AddressBookResponse addAddress(AddAddressRequest request, String userId) {
        CustomerAccount account = resolveAccount(userId);

        AddressScreenResponseDto screening = chainalysisClient.screenAddress(
                new AddressScreenRequestDto(request.walletAddress(), request.currency().name(), "POLYGON", "outgoing")
        );

        if (!screening.approved()) {
            log.warn("[ADDRESS-BOOK] Blocked high-risk address={} riskScore={} user={}",
                    request.walletAddress(), screening.riskScore(), userId);
            writeAuditLog(UUID.randomUUID(), "ADDRESS_SCREENING_BLOCKED", userId,
                    "Screening blockiert: address=" + request.walletAddress() + ", riskScore=" + screening.riskScore());
            throw new ComplianceBlockException(request.walletAddress(), screening.riskScore());
        }

        AddressBook entry = new AddressBook();
        entry.setCustomerAccount(account);
        entry.setLabel(request.label());
        entry.setWalletAddress(request.walletAddress());
        entry.setCurrency(request.currency());
        entry.setRiskScore(RiskScore.valueOf(screening.riskScore()));

        AddressBook saved = addressBookRepository.save(entry);

        writeAuditLog(saved.getId(), "ADDRESS_ADDED", userId,
                "Adresse hinzugefügt: address=" + saved.getWalletAddress()
                        + ", label=" + saved.getLabel()
                        + ", riskScore=" + saved.getRiskScore());

        log.info("[ADDRESS-BOOK] Added address={} riskScore={} user={}", saved.getWalletAddress(), saved.getRiskScore(), userId);
        return toResponse(saved);
    }

    private AddressBookResponse addAddressFallback(AddAddressRequest request, String userId, Throwable ex) {
        if (ex instanceof ComplianceBlockException cbe) throw cbe;
        log.error("[ADDRESS-BOOK] Chainalysis unavailable — blocking address={} as precaution", request.walletAddress());
        throw new ComplianceBlockException(request.walletAddress(), "UNAVAILABLE");
    }

    private CustomerAccount resolveAccount(String userId) {
        String tenantId = TenantContext.get();
        java.util.Optional<CustomerAccount> opt = (tenantId != null && !tenantId.isBlank())
                ? accountRepository.findByCustomerIdAndTenantId(userId, tenantId)
                : accountRepository.findByCustomerId(userId);
        return opt.orElseThrow(() -> new NoSuchElementException("No account for user: " + userId));
    }

    @Transactional(readOnly = true)
    public List<AddressBookResponse> listAddresses(String userId) {
        String tenantId = TenantContext.get();
        java.util.Optional<CustomerAccount> accountOpt = (tenantId != null && !tenantId.isBlank())
                ? accountRepository.findByCustomerIdAndTenantId(userId, tenantId)
                : accountRepository.findByCustomerId(userId);
        return accountOpt
                .map(account -> addressBookRepository
                        .findByCustomerAccountIdAndStatus(account.getId(), AddressStatus.ACTIVE)
                        .stream().map(this::toResponse).toList())
                .orElse(List.of());
    }

    @Transactional
    public void revokeAddress(UUID addressId, String userId) {
        AddressBook address = addressBookRepository.findById(addressId)
                .orElseThrow(() -> new NoSuchElementException("Address not found: " + addressId));

        address.setStatus(AddressStatus.REVOKED);
        addressBookRepository.save(address);

        writeAuditLog(addressId, "ADDRESS_REVOKED", userId,
                "Adresse widerrufen: address=" + address.getWalletAddress() + ", label=" + address.getLabel());

        log.info("[ADDRESS-BOOK] Revoked address={} user={}", address.getWalletAddress(), userId);
    }

    private void writeAuditLog(UUID entityId, String action, String userId, String details) {
        AuditLog entry = new AuditLog();
        entry.setEntityType("AddressBook");
        entry.setEntityId(entityId);
        entry.setAction(action);
        entry.setUserId(userId);
        entry.setDetails(details);
        auditLogRepository.save(entry);
    }

    private AddressBookResponse toResponse(AddressBook a) {
        return new AddressBookResponse(
                a.getId(), a.getLabel(), a.getWalletAddress(),
                a.getCurrency(), a.getRiskScore(), a.getStatus(), a.getVerifiedAt()
        );
    }
}
