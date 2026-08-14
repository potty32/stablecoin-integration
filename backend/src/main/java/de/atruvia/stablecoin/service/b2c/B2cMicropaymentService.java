package de.atruvia.stablecoin.service.b2c;

import de.atruvia.stablecoin.client.CircleWalletClient;
import de.atruvia.stablecoin.client.dto.CircleTransferRequestDto;
import de.atruvia.stablecoin.client.dto.CircleTransferResponseDto;
import de.atruvia.stablecoin.client.dto.CircleWalletBalanceDto;
import de.atruvia.stablecoin.dto.request.b2c.MicropaymentRequest;
import de.atruvia.stablecoin.dto.response.CardWalletResponse;
import de.atruvia.stablecoin.dto.response.TransactionResponse;
import de.atruvia.stablecoin.entity.*;
import de.atruvia.stablecoin.exception.IdempotencyConflictException;
import de.atruvia.stablecoin.repository.AuditLogRepository;
import de.atruvia.stablecoin.repository.CustomerAccountRepository;
import de.atruvia.stablecoin.repository.StablecoinTransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class B2cMicropaymentService {

    private static final Logger log = LoggerFactory.getLogger(B2cMicropaymentService.class);

    private static final BigDecimal MAX_MICROPAYMENT_EUR = BigDecimal.TEN;
    private static final BigDecimal MICROPAYMENT_FEE = new BigDecimal("0.10");
    private static final String MERCHANT_WALLET_PREFIX = "0xMerchantL2Wallet";

    private final StablecoinTransactionRepository txRepository;
    private final CustomerAccountRepository accountRepository;
    private final AuditLogRepository auditLogRepository;
    private final CircleWalletClient circleWalletClient;

    public B2cMicropaymentService(
            StablecoinTransactionRepository txRepository,
            CustomerAccountRepository accountRepository,
            AuditLogRepository auditLogRepository,
            CircleWalletClient circleWalletClient) {
        this.txRepository = txRepository;
        this.accountRepository = accountRepository;
        this.auditLogRepository = auditLogRepository;
        this.circleWalletClient = circleWalletClient;
    }

    @Transactional
    public TransactionResponse pay(String idempotencyKey, MicropaymentRequest request, String userId) {
        if (request.biometricToken() == null || request.biometricToken().length() < 10) {
            throw new IllegalArgumentException("biometricToken must be at least 10 characters");
        }
        if (request.amountEur().compareTo(MAX_MICROPAYMENT_EUR) > 0) {
            throw new IllegalArgumentException(
                    String.format("Micropayment amount %.2f EUR exceeds maximum of %.2f EUR",
                            request.amountEur(), MAX_MICROPAYMENT_EUR));
        }

        txRepository.findByIdempotencyKey(idempotencyKey)
                .ifPresent(ex -> { throw new IdempotencyConflictException(ex.getId()); });

        CustomerAccount account = accountRepository.findByIban(request.sourceIban())
                .orElseThrow(() -> new NoSuchElementException("Account not found for IBAN: " + request.sourceIban()));

        String merchantWallet = resolveMerchantWallet(request.destinationMerchantId());

        StablecoinTransaction tx = new StablecoinTransaction();
        tx.setIdempotencyKey(idempotencyKey);
        tx.setCustomerAccount(account);
        tx.setType(TransactionType.P2P);
        tx.setCurrency(StablecoinCurrency.USDC);
        tx.setAmountFiat(request.amountEur());
        tx.setAmountStablecoin(request.amountEur().setScale(6, RoundingMode.HALF_UP));
        tx.setSourceWallet(account.getWalletAddress());
        tx.setDestinationWallet(merchantWallet);
        tx.setTransactionFee(MICROPAYMENT_FEE);
        tx.setStatus(TransactionStatus.PROCESSING);
        StablecoinTransaction savedTx = txRepository.save(tx);

        CircleTransferResponseDto circleResponse = circleWalletClient.initiateTransfer(
                new CircleTransferRequestDto(
                        idempotencyKey,
                        new CircleTransferRequestDto.Source("wallet", "B2C_MASTER_WALLET_ID"),
                        new CircleTransferRequestDto.Destination("blockchain", merchantWallet, "MATIC"),
                        new CircleTransferRequestDto.Amount(request.amountEur().toPlainString(), "USDC")));

        savedTx.setStatus(TransactionStatus.SETTLED);
        savedTx.setCircleTransactionId(circleResponse.id());
        savedTx.setSettledAt(LocalDateTime.now());
        savedTx.setGrossRevenue(MICROPAYMENT_FEE);
        txRepository.save(savedTx);

        saveAuditLog(savedTx.getId(), "MICROPAYMENT_SETTLED", null,
                String.format("{\"status\":\"SETTLED\",\"amount\":\"%s\",\"merchant\":\"%s\",\"content\":\"%s\"}",
                        request.amountEur(), request.destinationMerchantId(), request.contentId()),
                userId);

        log.info("[B2C-MICROPAY] SETTLED tx={} amount={}EUR merchant={}", savedTx.getId(), request.amountEur(), request.destinationMerchantId());

        return new TransactionResponse(
                savedTx.getId(), savedTx.getType(), savedTx.getStatus(),
                savedTx.getAmountFiat(), savedTx.getAmountStablecoin(), savedTx.getCurrency(),
                savedTx.getBlockchainHash(), savedTx.getGrossRevenue(), false,
                savedTx.getCreatedAt(), savedTx.getSettledAt(), Collections.emptyList()
        );
    }

    @Transactional(readOnly = true)
    public CardWalletResponse getCardWallet(String customerId) {
        CustomerAccount account = accountRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new NoSuchElementException("Account not found for customer: " + customerId));

        CircleWalletBalanceDto balance = circleWalletClient.getWalletBalance(account.getWalletAddress());

        String usdc = balance.balances().stream()
                .filter(b -> "USDC".equalsIgnoreCase(b.currency()))
                .map(CircleWalletBalanceDto.Balance::amount)
                .findFirst()
                .orElse("0.000000");

        String eurc = balance.balances().stream()
                .filter(b -> "EURC".equalsIgnoreCase(b.currency()))
                .map(CircleWalletBalanceDto.Balance::amount)
                .findFirst()
                .orElse("0.000000");

        return new CardWalletResponse(account.getWalletAddress(), usdc, eurc);
    }

    private String resolveMerchantWallet(String merchantId) {
        String sanitized = merchantId.replaceAll("[^a-zA-Z0-9]", "");
        String padded = (sanitized + "0000000000000000000000").substring(0, 22);
        return MERCHANT_WALLET_PREFIX + padded;
    }

    private void saveAuditLog(UUID entityId, String action, String previousState, String newState, String userId) {
        AuditLog entry = new AuditLog();
        entry.setEntityType("StablecoinTransaction");
        entry.setEntityId(entityId);
        entry.setAction(action);
        entry.setPreviousState(previousState);
        entry.setNewState(newState);
        entry.setUserId(userId);
        auditLogRepository.save(entry);
    }
}
