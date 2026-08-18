package de.atruvia.stablecoin;

import de.atruvia.stablecoin.config.TenantContext;
import de.atruvia.stablecoin.dto.request.b2b.ApproveTransferRequest;
import de.atruvia.stablecoin.dto.request.b2b.InitiateTransferRequest;
import de.atruvia.stablecoin.dto.response.TransactionResponse;
import de.atruvia.stablecoin.entity.*;
import de.atruvia.stablecoin.exception.ComplianceBlockException;
import de.atruvia.stablecoin.repository.*;
import de.atruvia.stablecoin.service.b2b.B2bTransferService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// KEIN @Transactional auf Klassenebene: REQUIRES_NEW-Service-Methoden brauchen committed Daten
class B2bTransferIntegrationTest extends AbstractLocalDbTest {

    @Autowired B2bTransferService transferService;
    @Autowired CustomerAccountRepository accountRepository;
    @Autowired AddressBookRepository addressBookRepository;
    @Autowired StablecoinTransactionRepository txRepository;
    @Autowired AuditLogRepository auditLogRepository;
    @Autowired ApprovalWorkflowRepository approvalRepository;
    @Autowired OutboxMessageRepository outboxRepository;
    @Autowired YieldPositionRepository yieldPositionRepository;

    private CustomerAccount b2bAccount;
    private static final String B2B_IBAN = "DE89370400440532013000";
    private static final String LOW_RISK_WALLET = "0xA100000000000000000000000000000000000001";
    private static final String DEAD_WALLET = "0xDEAD000000000000000000000000000000000000";

    @BeforeEach
    void setUp() {
        TenantContext.set("tenant-default");
        b2bAccount = accountRepository.findByIban(B2B_IBAN).orElseThrow();
    }

    @AfterEach
    void cleanup() {
        TenantContext.clear();
        // Reihenfolge nach FK-Abhängigkeiten
        outboxRepository.deleteAll();
        auditLogRepository.deleteAll();
        approvalRepository.deleteAll();
        yieldPositionRepository.deleteAll();
        txRepository.deleteAll();
    }

    private AddressBook whitelistWallet(String wallet) {
        // findOrCreate: verhindert UniqueConstraint-Fehler bei mehrfachen Test-Ausführungen
        return addressBookRepository
                .findByCustomerAccountIdAndWalletAddressAndStatus(
                        b2bAccount.getId(), wallet, AddressStatus.ACTIVE)
                .orElseGet(() -> {
                    AddressBook entry = new AddressBook();
                    entry.setCustomerAccount(b2bAccount);
                    entry.setLabel("Test Wallet");
                    entry.setWalletAddress(wallet);
                    entry.setCurrency(StablecoinCurrency.USDC);
                    entry.setRiskScore(RiskScore.LOW);
                    entry.setStatus(AddressStatus.ACTIVE);
                    return addressBookRepository.save(entry);
                });
    }

    // TC1: Happy-Path — Transfer < 25.000 EUR → SETTLED
    @Test
    void happyPath_transferBelowThreshold_settles() {
        whitelistWallet(LOW_RISK_WALLET);

        TransactionResponse result = transferService.initiate(
                UUID.randomUUID().toString(),
                new InitiateTransferRequest(B2B_IBAN, LOW_RISK_WALLET,
                        new BigDecimal("1000"), StablecoinCurrency.USDC, null, null, "Test", null, null, null),
                "cust-b2b-001");

        assertThat(result.status()).isEqualTo(TransactionStatus.SETTLED);
        assertThat(result.blockchainHash()).isNotBlank();
        assertThat(txRepository.findById(result.transactionId()))
                .isPresent()
                .get()
                .extracting(StablecoinTransaction::getStatus)
                .isEqualTo(TransactionStatus.SETTLED);
    }

    // TC2: Whitelist-Block — kein TX-Record in DB
    @Test
    void whitelistBlock_noTxWritten() {
        long before = txRepository.count();

        assertThatThrownBy(() -> transferService.initiate(
                UUID.randomUUID().toString(),
                new InitiateTransferRequest(B2B_IBAN, "0xNOT_WHITELISTED_WALLET_ADDRESS_0001",
                        new BigDecimal("500"), StablecoinCurrency.USDC, null, null, "Test", null, null, null),
                "cust-b2b-001"))
                .isInstanceOf(ComplianceBlockException.class)
                .hasMessageContaining("NOT_WHITELISTED");

        assertThat(txRepository.count()).isEqualTo(before);
    }

    // TC3: Vier-Augen — > 25.000 EUR → PENDING_APPROVAL → approve → SETTLED
    @Test
    void approvalWorkflow_aboveThreshold_approveThenSettles() {
        whitelistWallet(LOW_RISK_WALLET);

        // Initiate: Betrag über Threshold (25.000 EUR)
        TransactionResponse pending = transferService.initiate(
                UUID.randomUUID().toString(),
                new InitiateTransferRequest(B2B_IBAN, LOW_RISK_WALLET,
                        new BigDecimal("50000"), StablecoinCurrency.USDC, null, null, "Bulk", null, null, null),
                "cust-b2b-001");

        assertThat(pending.status()).isEqualTo(TransactionStatus.PENDING_APPROVAL);

        // Approve durch anderen User (dev-mode=true → self-approval erlaubt)
        TransactionResponse settled = transferService.approve(
                pending.transactionId(),
                new ApproveTransferRequest("cust-approver-001"));

        assertThat(settled.status()).isEqualTo(TransactionStatus.SETTLED);
    }

    // TC4: Chainalysis-Block (0xDEAD...) — TX landet mit Status BLOCKED
    @Test
    void complianceBlock_deadWallet_txIsBlocked() {
        // 0xDEAD... direkt in Whitelist (Chainalysis-Screen überspringen)
        // Suche nach beliebigem Status (ggf. REVOKED vom Sanctions-Batch) und reaktiviere
        var existingDead = addressBookRepository.findAll().stream()
                .filter(a -> a.getCustomerAccount().getId().equals(b2bAccount.getId())
                        && DEAD_WALLET.equals(a.getWalletAddress()))
                .findFirst();
        if (existingDead.isPresent()) {
            existingDead.get().setStatus(AddressStatus.ACTIVE);
            addressBookRepository.save(existingDead.get());
        } else {
            AddressBook deadEntry = new AddressBook();
            deadEntry.setCustomerAccount(b2bAccount);
            deadEntry.setLabel("Dead wallet");
            deadEntry.setWalletAddress(DEAD_WALLET);
            deadEntry.setCurrency(StablecoinCurrency.USDC);
            deadEntry.setRiskScore(RiskScore.LOW);
            deadEntry.setStatus(AddressStatus.ACTIVE);
            addressBookRepository.save(deadEntry);
        }

        assertThatThrownBy(() -> transferService.initiate(
                UUID.randomUUID().toString(),
                new InitiateTransferRequest(B2B_IBAN, DEAD_WALLET,
                        new BigDecimal("100"), StablecoinCurrency.USDC, null, null, "Test", null, null, null),
                "cust-b2b-001"))
                .isInstanceOf(ComplianceBlockException.class);

        // TX wurde geschrieben (PENDING) und dann BLOCKED gesetzt
        assertThat(txRepository.findAll())
                .anyMatch(tx -> tx.getDestinationWallet().equals(DEAD_WALLET)
                        && tx.getStatus() == TransactionStatus.FAILED);
    }
}
