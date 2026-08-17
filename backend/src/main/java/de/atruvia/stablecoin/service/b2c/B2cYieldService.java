package de.atruvia.stablecoin.service.b2c;

import de.atruvia.stablecoin.dto.request.b2c.YieldDepositRequest;
import de.atruvia.stablecoin.dto.response.YieldPositionResponse;
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
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class B2cYieldService {

    private static final Logger log = LoggerFactory.getLogger(B2cYieldService.class);

    private static final BigDecimal ANNUAL_YIELD_RATE = new BigDecimal("3.5");
    private static final BigDecimal ANNUAL_RATE_DECIMAL = new BigDecimal("0.035");
    private static final BigDecimal DAYS_PER_YEAR = new BigDecimal("365");
    private static final String RWA_FUND_WALLET = "0xRWAMoneyMarketFund000000000000000000001";
    private static final String STATUS_ACTIVE = "ACTIVE";

    private final StablecoinTransactionRepository txRepository;
    private final CustomerAccountRepository accountRepository;
    private final AuditLogRepository auditLogRepository;

    public B2cYieldService(
            StablecoinTransactionRepository txRepository,
            CustomerAccountRepository accountRepository,
            AuditLogRepository auditLogRepository) {
        this.txRepository = txRepository;
        this.accountRepository = accountRepository;
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional
    public YieldPositionResponse deposit(String idempotencyKey, YieldDepositRequest request, String userId) {
        txRepository.findByIdempotencyKey(idempotencyKey)
                .ifPresent(ex -> { throw new IdempotencyConflictException(ex.getId()); });

        CustomerAccount account = accountRepository.findByIban(request.sourceIban())
                .orElseThrow(() -> new NoSuchElementException("Account not found for IBAN: " + request.sourceIban()));

        StablecoinTransaction tx = new StablecoinTransaction();
        tx.setIdempotencyKey(idempotencyKey);
        tx.setCustomerAccount(account);
        tx.setType(TransactionType.YIELD_DEPOSIT);
        tx.setCurrency(StablecoinCurrency.EURC);
        tx.setAmountFiat(request.amountEur());
        tx.setAmountStablecoin(request.amountEur().setScale(6, RoundingMode.HALF_UP));
        tx.setSourceWallet(account.getWalletAddress());
        tx.setDestinationWallet(RWA_FUND_WALLET);
        tx.setStatus(TransactionStatus.SETTLED);
        tx.setSettledAt(LocalDateTime.now());
        StablecoinTransaction savedTx = txRepository.save(tx);

        saveAuditLog(savedTx.getId(), "YIELD_DEPOSIT_CREATED", null,
                String.format("{\"status\":\"SETTLED\",\"amount\":\"%s\",\"annualRate\":\"%s%%\"}",
                        request.amountEur(), ANNUAL_YIELD_RATE),
                userId);

        log.info("[B2C-YIELD] Deposit SETTLED tx={} amount={}EUR", savedTx.getId(), request.amountEur());

        BigDecimal dailyYield = request.amountEur()
                .multiply(ANNUAL_RATE_DECIMAL)
                .divide(DAYS_PER_YEAR, MathContext.DECIMAL64)
                .setScale(6, RoundingMode.HALF_UP);
        return new YieldPositionResponse(
                savedTx.getId(),
                request.amountEur(),
                request.amountEur(),
                dailyYield,
                ANNUAL_YIELD_RATE,
                STATUS_ACTIVE,
                savedTx.getSettledAt()
        );
    }

    @Transactional(readOnly = true)
    public YieldPositionResponse getPosition(String customerId) {
        CustomerAccount account = accountRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new NoSuchElementException("Account not found for customer: " + customerId));

        StablecoinTransaction tx = txRepository
                .findTopByCustomerAccountIdAndTypeAndStatusOrderByCreatedAtDesc(
                        account.getId(), TransactionType.YIELD_DEPOSIT, TransactionStatus.SETTLED)
                .orElseThrow(() -> new NoSuchElementException("No active yield position found for customer: " + customerId));

        return calculatePosition(tx);
    }

    @Transactional
    public void redeem(UUID transactionId, String userId) {
        StablecoinTransaction tx = txRepository.findById(transactionId)
                .orElseThrow(() -> new NoSuchElementException("Yield position not found: " + transactionId));

        if (tx.getType() != TransactionType.YIELD_DEPOSIT) {
            throw new NoSuchElementException("Transaction is not a yield deposit: " + transactionId);
        }
        if (tx.getStatus() != TransactionStatus.SETTLED) {
            throw new IllegalStateException("Yield position is not active (status=" + tx.getStatus() + ")");
        }

        long days = ChronoUnit.DAYS.between(tx.getSettledAt(), LocalDateTime.now());
        BigDecimal currentValue = computeCurrentValue(tx.getAmountFiat(), days);
        BigDecimal accrued = currentValue.subtract(tx.getAmountFiat()).setScale(6, RoundingMode.HALF_UP);

        tx.setStatus(TransactionStatus.REDEEMED);
        txRepository.save(tx);

        saveAuditLog(tx.getId(), "YIELD_REDEEMED",
                String.format("{\"status\":\"SETTLED\",\"principal\":\"%s\"}", tx.getAmountFiat()),
                String.format("{\"status\":\"REDEEMED\",\"accruedYield\":\"%s\",\"daysSinceDeposit\":%d}", accrued, days),
                userId);

        log.info("[B2C-YIELD] Redeemed tx={} days={} accruedYield={}EUR", transactionId, days, accrued);
    }

    private YieldPositionResponse calculatePosition(StablecoinTransaction tx) {
        LocalDateTime depositedAt = tx.getSettledAt() != null ? tx.getSettledAt() : tx.getCreatedAt();
        long days = ChronoUnit.DAYS.between(depositedAt, LocalDateTime.now());
        BigDecimal principal = tx.getAmountFiat();
        BigDecimal currentValue = computeCurrentValue(principal, days);
        BigDecimal accrued = currentValue.subtract(principal).setScale(6, RoundingMode.HALF_UP);

        BigDecimal dailyYield = principal
                .multiply(ANNUAL_RATE_DECIMAL)
                .divide(DAYS_PER_YEAR, MathContext.DECIMAL64)
                .setScale(6, RoundingMode.HALF_UP);

        return new YieldPositionResponse(
                tx.getId(),
                principal,
                currentValue,
                dailyYield,
                ANNUAL_YIELD_RATE,
                STATUS_ACTIVE,
                depositedAt
        );
    }

    private BigDecimal computeCurrentValue(BigDecimal principal, long days) {
        // currentValue = principal * (1 + 0.035/365)^days  — using BigDecimal with DECIMAL64
        BigDecimal dailyRate = ANNUAL_RATE_DECIMAL.divide(DAYS_PER_YEAR, MathContext.DECIMAL64);
        BigDecimal factor = BigDecimal.ONE.add(dailyRate, MathContext.DECIMAL64)
                .pow((int) Math.min(days, Integer.MAX_VALUE), MathContext.DECIMAL64);
        return principal.multiply(factor, MathContext.DECIMAL64).setScale(6, RoundingMode.HALF_UP);
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
