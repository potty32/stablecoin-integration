package de.atruvia.stablecoin.service.b2b;

import de.atruvia.stablecoin.client.ChainalysisClient;
import de.atruvia.stablecoin.client.N8nWebhookClient;
import de.atruvia.stablecoin.client.dto.AddressScreenRequestDto;
import de.atruvia.stablecoin.client.dto.AddressScreenResponseDto;
import de.atruvia.stablecoin.entity.AddressBook;
import de.atruvia.stablecoin.entity.AddressStatus;
import de.atruvia.stablecoin.entity.AuditLog;
import de.atruvia.stablecoin.repository.AddressBookRepository;
import de.atruvia.stablecoin.repository.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class SanctionsBatchService {

    private static final Logger log = LoggerFactory.getLogger(SanctionsBatchService.class);

    private final AddressBookRepository addressBookRepository;
    private final ChainalysisClient chainalysisClient;
    private final AuditLogRepository auditLogRepository;
    private final N8nWebhookClient n8nWebhookClient;

    public SanctionsBatchService(
            AddressBookRepository addressBookRepository,
            ChainalysisClient chainalysisClient,
            AuditLogRepository auditLogRepository,
            N8nWebhookClient n8nWebhookClient) {
        this.addressBookRepository = addressBookRepository;
        this.chainalysisClient = chainalysisClient;
        this.auditLogRepository = auditLogRepository;
        this.n8nWebhookClient = n8nWebhookClient;
    }

    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional
    public void runNightlySanctionsScan() {
        List<AddressBook> active = addressBookRepository.findByStatus(AddressStatus.ACTIVE);
        log.info("[SANCTIONS-BATCH] Starting nightly scan for {} active addresses", active.size());

        int revoked = 0;
        for (AddressBook address : active) {
            try {
                if (screenAndRevokeIfHighRisk(address)) revoked++;
            } catch (Exception e) {
                log.error("[SANCTIONS-BATCH] Error screening address={}: {}",
                        address.getWalletAddress(), e.getMessage());
            }
        }
        log.info("[SANCTIONS-BATCH] Scan complete. revoked={}/{}", revoked, active.size());
    }

    private boolean screenAndRevokeIfHighRisk(AddressBook address) {
        AddressScreenResponseDto result = chainalysisClient.screenAddress(
                new AddressScreenRequestDto(
                        address.getWalletAddress(),
                        address.getCurrency().name(),
                        "POLYGON",
                        "outgoing"));

        if (!result.approved()) {
            address.setStatus(AddressStatus.REVOKED);
            addressBookRepository.save(address);

            AuditLog entry = new AuditLog();
            entry.setEntityType("AddressBook");
            entry.setEntityId(address.getId());
            entry.setAction("SANCTIONS_BATCH_REVOKED");
            entry.setUserId("SYSTEM");
            entry.setDetails("Sanctions-Batch: address=" + address.getWalletAddress()
                    + ", riskScore=" + result.riskScore()
                    + ", source=NIGHTLY_BATCH");
            auditLogRepository.save(entry);

            try {
                n8nWebhookClient.notifyAddressRevoked(
                        address.getWalletAddress(),
                        address.getCustomerAccount().getCustomerId(),
                        result.riskScore());
            } catch (Exception e) {
                log.warn("[SANCTIONS-BATCH] n8n notification failed for address={}: {}",
                        address.getWalletAddress(), e.getMessage());
            }

            log.warn("[SANCTIONS-BATCH] REVOKED address={} riskScore={} customer={}",
                    address.getWalletAddress(), result.riskScore(),
                    address.getCustomerAccount().getCustomerId());
            return true;
        }
        return false;
    }
}
