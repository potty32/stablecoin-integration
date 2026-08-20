package de.atruvia.stablecoin.service.common;

import de.atruvia.stablecoin.client.CircleWalletClient;
import de.atruvia.stablecoin.client.CoreBankingClient;
import de.atruvia.stablecoin.client.dto.CircleWalletBalanceDto;
import de.atruvia.stablecoin.dto.response.AccountBalanceResponse;
import de.atruvia.stablecoin.dto.response.TransactionResponse;
import de.atruvia.stablecoin.entity.CustomerAccount;
import de.atruvia.stablecoin.entity.StablecoinTransaction;
import de.atruvia.stablecoin.repository.ApprovalWorkflowRepository;
import de.atruvia.stablecoin.repository.AuditLogRepository;
import de.atruvia.stablecoin.repository.CustomerAccountRepository;
import de.atruvia.stablecoin.repository.StablecoinTransactionRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * T-05-Fix: BMAD-Schichtenarchitektur — Business-Logik aus CommonController extrahiert.
 * Ownership-Prüfung, Balance-Aggregation und Timeline-Building gehören in den Service.
 */
@Service
public class AccountService {

    private final CustomerAccountRepository accountRepository;
    private final StablecoinTransactionRepository txRepository;
    private final ApprovalWorkflowRepository approvalRepository;
    private final AuditLogRepository auditLogRepository;
    private final CoreBankingClient coreBankingClient;
    private final CircleWalletClient circleWalletClient;

    public AccountService(CustomerAccountRepository accountRepository,
                          StablecoinTransactionRepository txRepository,
                          ApprovalWorkflowRepository approvalRepository,
                          AuditLogRepository auditLogRepository,
                          CoreBankingClient coreBankingClient,
                          CircleWalletClient circleWalletClient) {
        this.accountRepository   = accountRepository;
        this.txRepository        = txRepository;
        this.approvalRepository  = approvalRepository;
        this.auditLogRepository  = auditLogRepository;
        this.coreBankingClient   = coreBankingClient;
        this.circleWalletClient  = circleWalletClient;
    }

    /** Prüft Ownership und liefert Fiat + Stablecoin-Guthaben. */
    public AccountBalanceResponse getBalanceForCustomer(String iban, String customerId) {
        CustomerAccount account = accountRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new NoSuchElementException("No account for user: " + customerId));
        if (!account.getIban().equals(iban)) {
            throw new AccessDeniedException("Access denied to account: " + iban);
        }

        var fiatBalance = coreBankingClient.getAccountBalance(iban);

        // TODO (prod): Wallet-ID aus CustomerAccount.walletAddress laden
        CircleWalletBalanceDto walletBalance = circleWalletClient.getWalletBalance("BANK_MASTER_WALLET_ID");
        Map<String, String> stablecoinBalances = walletBalance.balances().stream()
                .collect(Collectors.toMap(CircleWalletBalanceDto.Balance::currency,
                        CircleWalletBalanceDto.Balance::amount));

        return new AccountBalanceResponse(iban, fiatBalance.balanceEur(), stablecoinBalances);
    }

    /** Prüft Ownership (intra-tenant) und liefert TX mit Timeline — @Transactional für konsistenten Snapshot. */
    @Transactional(readOnly = true)
    public TransactionResponse getTransactionForCustomer(UUID txId, String customerId) {
        StablecoinTransaction tx = txRepository.findById(txId)
                .orElseThrow(() -> new NoSuchElementException("Transaction not found: " + txId));

        CustomerAccount account = accountRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new NoSuchElementException("No account for user: " + customerId));
        if (!tx.getCustomerAccount().getId().equals(account.getId())) {
            throw new AccessDeniedException("Access denied to transaction: " + txId);
        }

        boolean requiresApproval = approvalRepository.findByTransactionId(txId).isPresent();
        List<TransactionResponse.TimelineEntry> timeline = buildTimeline(txId);

        return new TransactionResponse(
                tx.getId(), tx.getType(), tx.getStatus(),
                tx.getAmountFiat(), tx.getAmountStablecoin(), tx.getCurrency(),
                tx.getBlockchainHash(), tx.getGrossRevenue(), requiresApproval,
                tx.getCreatedAt(), tx.getSettledAt(), timeline);
    }

    private List<TransactionResponse.TimelineEntry> buildTimeline(UUID txId) {
        return auditLogRepository.findByTransactionIdOrderByTimestampAsc(txId)
                .stream()
                .filter(e -> e.getToStatus() != null)
                .map(e -> new TransactionResponse.TimelineEntry(
                        e.getFromStatus(), e.getToStatus(),
                        e.getUserId(), e.getTimestamp(), e.getDetails()))
                .collect(Collectors.toList());
    }
}
