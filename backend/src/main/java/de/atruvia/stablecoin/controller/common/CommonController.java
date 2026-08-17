package de.atruvia.stablecoin.controller.common;

import de.atruvia.stablecoin.client.CircleWalletClient;
import de.atruvia.stablecoin.client.CoreBankingClient;
import de.atruvia.stablecoin.client.dto.CircleWalletBalanceDto;
import de.atruvia.stablecoin.dto.response.AccountBalanceResponse;
import de.atruvia.stablecoin.dto.response.TransactionResponse;
import de.atruvia.stablecoin.entity.AuditLog;
import de.atruvia.stablecoin.entity.CustomerAccount;
import de.atruvia.stablecoin.entity.StablecoinTransaction;
import de.atruvia.stablecoin.entity.TransactionStatus;
import de.atruvia.stablecoin.repository.ApprovalWorkflowRepository;
import de.atruvia.stablecoin.repository.AuditLogRepository;
import de.atruvia.stablecoin.repository.CustomerAccountRepository;
import de.atruvia.stablecoin.repository.StablecoinTransactionRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1")
public class CommonController {

    private static final Pattern STATUS_PATTERN = Pattern.compile("\"status\"\\s*:\\s*\"([^\"]+)\"");

    private final CustomerAccountRepository accountRepository;
    private final StablecoinTransactionRepository txRepository;
    private final ApprovalWorkflowRepository approvalRepository;
    private final AuditLogRepository auditLogRepository;
    private final CoreBankingClient coreBankingClient;
    private final CircleWalletClient circleWalletClient;

    public CommonController(
            CustomerAccountRepository accountRepository,
            StablecoinTransactionRepository txRepository,
            ApprovalWorkflowRepository approvalRepository,
            AuditLogRepository auditLogRepository,
            CoreBankingClient coreBankingClient,
            CircleWalletClient circleWalletClient) {
        this.accountRepository = accountRepository;
        this.txRepository = txRepository;
        this.approvalRepository = approvalRepository;
        this.auditLogRepository = auditLogRepository;
        this.coreBankingClient = coreBankingClient;
        this.circleWalletClient = circleWalletClient;
    }

    @GetMapping("/accounts/{iban}/balance")
    public ResponseEntity<AccountBalanceResponse> getBalance(
            @PathVariable String iban,
            Authentication auth) {
        CustomerAccount requestingAccount = accountRepository.findByCustomerId(auth.getName())
                .orElseThrow(() -> new NoSuchElementException("No account for user: " + auth.getName()));
        if (!requestingAccount.getIban().equals(iban)) {
            throw new AccessDeniedException("Access denied to account: " + iban);
        }

        var fiatBalance = coreBankingClient.getAccountBalance(iban);

        // In Prod: Wallet-ID aus Account laden, hier als Vereinfachung fix
        CircleWalletBalanceDto walletBalance = circleWalletClient.getWalletBalance("BANK_MASTER_WALLET_ID");
        Map<String, String> stablecoinBalances = walletBalance.balances().stream()
                .collect(Collectors.toMap(CircleWalletBalanceDto.Balance::currency,
                        CircleWalletBalanceDto.Balance::amount));

        return ResponseEntity.ok(new AccountBalanceResponse(iban, fiatBalance.balanceEur(), stablecoinBalances));
    }

    @GetMapping("/transactions/{id}")
    public ResponseEntity<TransactionResponse> getTransaction(
            @PathVariable UUID id,
            Authentication auth) {
        StablecoinTransaction tx = txRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Transaction not found: " + id));

        CustomerAccount requestingAccount = accountRepository.findByCustomerId(auth.getName())
                .orElseThrow(() -> new NoSuchElementException("No account for user: " + auth.getName()));
        if (!tx.getCustomerAccount().getId().equals(requestingAccount.getId())) {
            throw new AccessDeniedException("Access denied to transaction: " + id);
        }

        boolean requiresApproval = approvalRepository.findByTransactionId(id).isPresent();

        return ResponseEntity.ok(new TransactionResponse(
                tx.getId(),
                tx.getType(),
                tx.getStatus(),
                tx.getAmountFiat(),
                tx.getAmountStablecoin(),
                tx.getCurrency(),
                tx.getBlockchainHash(),
                tx.getGrossRevenue(),
                requiresApproval,
                tx.getCreatedAt(),
                tx.getSettledAt(),
                buildTimeline(tx.getId())
        ));
    }

    private List<TransactionResponse.TimelineEntry> buildTimeline(UUID txId) {
        List<AuditLog> entries =
                auditLogRepository.findByEntityTypeAndEntityIdOrderByTimestampAsc("StablecoinTransaction", txId);

        Map<TransactionStatus, LocalDateTime> seen = new LinkedHashMap<>();
        for (AuditLog entry : entries) {
            if (entry.getNewState() == null) continue;
            Matcher m = STATUS_PATTERN.matcher(entry.getNewState());
            if (!m.find()) continue;
            try {
                TransactionStatus status = TransactionStatus.valueOf(m.group(1));
                seen.putIfAbsent(status, entry.getTimestamp());
            } catch (IllegalArgumentException ignored) {}
        }
        return seen.entrySet().stream()
                .map(e -> new TransactionResponse.TimelineEntry(e.getKey(), e.getValue()))
                .toList();
    }
}
