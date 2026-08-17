package de.atruvia.stablecoin.service.b2c;

import de.atruvia.stablecoin.dto.request.b2c.YieldDepositRequest;
import de.atruvia.stablecoin.dto.response.YieldPositionResponse;
import de.atruvia.stablecoin.entity.*;
import de.atruvia.stablecoin.exception.IdempotencyConflictException;
import de.atruvia.stablecoin.repository.AuditLogRepository;
import de.atruvia.stablecoin.repository.CustomerAccountRepository;
import de.atruvia.stablecoin.repository.StablecoinTransactionRepository;
import de.atruvia.stablecoin.repository.YieldPositionRepository;
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

    private final StablecoinTransactionRepository txRepository;
    private final CustomerAccountRepository accountRepository;
    private final AuditLogRepository auditLogRepository;
    private final YieldPositionRepository yieldPositionRepository;

    public B2cYieldService(
            StablecoinTransactionRepository txRepository,
            CustomerAccountRepository accountRepository,
            AuditLogRepository auditLogRepository,
            YieldPositionRepository yieldPositionRepository) {
        this.txRepository = txRepository;
        this.accountRepository = accountRepository;
        this.auditLogRepository = auditLogRepository;
        this.yieldPositionRepository = yieldPositionRepository;
    }

    // ── UC-16: Yield-Sparkonto eröffnen ──────────────────────────────────────

    @Transactional
    public YieldPositionResponse deposit(String idempotencyKey, YieldDepositRequest request, String userId) {
        txRepository.findByIdempotencyKey(idempotencyKey)
                .ifPresent(ex -> { throw new IdempotencyConflictException(ex.getId()); });

        CustomerAccount account = accountRepository.findByIban(request.sourceIban())
                .orElseThrow(() -> new NoSuchElementException("Account not found for IBAN: " + request.sourceIban()));

        // 1. YIELD_DEPOSIT Transaktion — unveränderlicher Buchungsbeleg
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

        // 2. YieldPosition — eigenständiger Positionslebenszyklus
        YieldPosition position = new YieldPosition();
        position.setCustomerAccount(account);
        position.setPrincipal(request.amountEur());
        position.setInterestRate(ANNUAL_RATE_DECIMAL);
        position.setDepositedAt(LocalDateTime.now());
        position.setStatus(YieldStatus.ACTIVE);
        position.setDepositTransactionId(savedTx.getId());
        YieldPosition savedPosition = yieldPositionRepository.save(position);

        saveAuditLog(savedPosition.getId(), "YieldPosition", "YIELD_DEPOSIT_CREATED", null,
                String.format("{\"status\":\"ACTIVE\",\"amount\":\"%s\",\"annualRate\":\"%s%%\",\"depositTxId\":\"%s\"}",
                        request.amountEur(), ANNUAL_YIELD_RATE, savedTx.getId()),
                userId);

        log.info("[B2C-YIELD] Deposit SETTLED depositTx={} position={} amount={}EUR",
                savedTx.getId(), savedPosition.getId(), request.amountEur());

        return calculatePosition(savedPosition);
    }

    // ── UC-18: Yield-Position abrufen ─────────────────────────────────────────

    @Transactional(readOnly = true)
    public YieldPositionResponse getPosition(String customerId) {
        CustomerAccount account = accountRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new NoSuchElementException("Account not found for customer: " + customerId));

        YieldPosition position = yieldPositionRepository
                .findByCustomerAccountIdAndStatus(account.getId(), YieldStatus.ACTIVE)
                .orElseThrow(() -> new NoSuchElementException("No active yield position found for customer: " + customerId));

        return calculatePosition(position);
    }

    // ── UC-17: Yield-Position auflösen ────────────────────────────────────────

    @Transactional
    public void redeem(UUID positionId, String userId) {
        YieldPosition position = yieldPositionRepository.findById(positionId)
                .orElseThrow(() -> new NoSuchElementException("Yield position not found: " + positionId));

        if (position.getStatus() != YieldStatus.ACTIVE) {
            throw new IllegalStateException("Yield position is not active (status=" + position.getStatus() + ")");
        }

        CustomerAccount account = position.getCustomerAccount();
        long days = ChronoUnit.DAYS.between(position.getDepositedAt(), LocalDateTime.now());
        BigDecimal currentValue = computeCurrentValue(position.getPrincipal(), days);
        BigDecimal accrued = currentValue.subtract(position.getPrincipal()).setScale(6, RoundingMode.HALF_UP);

        // 1. Eigenständige YIELD_REDEEM Transaktion — sauberer Buchungsbeleg für die Auszahlung
        StablecoinTransaction redeemTx = new StablecoinTransaction();
        redeemTx.setIdempotencyKey("redeem-" + positionId);
        redeemTx.setCustomerAccount(account);
        redeemTx.setType(TransactionType.YIELD_REDEEM);
        redeemTx.setCurrency(StablecoinCurrency.EURC);
        redeemTx.setAmountFiat(currentValue);
        redeemTx.setAmountStablecoin(currentValue.setScale(6, RoundingMode.HALF_UP));
        redeemTx.setSourceWallet(RWA_FUND_WALLET);
        redeemTx.setDestinationWallet(account.getWalletAddress());
        redeemTx.setStatus(TransactionStatus.REDEEMED);
        redeemTx.setSettledAt(LocalDateTime.now());
        StablecoinTransaction savedRedeemTx = txRepository.save(redeemTx);

        // 2. YieldPosition schließen
        position.setStatus(YieldStatus.CLOSED);
        position.setClosedAt(LocalDateTime.now());
        yieldPositionRepository.save(position);

        saveAuditLog(positionId, "YieldPosition", "YIELD_REDEEMED",
                String.format("{\"status\":\"ACTIVE\",\"principal\":\"%s\"}", position.getPrincipal()),
                String.format("{\"status\":\"CLOSED\",\"accruedYield\":\"%s\",\"daysSinceDeposit\":%d,\"redeemTxId\":\"%s\"}",
                        accrued, days, savedRedeemTx.getId()),
                userId);

        log.info("[B2C-YIELD] Redeemed position={} redeemTx={} days={} accruedYield={}EUR",
                positionId, savedRedeemTx.getId(), days, accrued);
    }

    // ── Berechnung ────────────────────────────────────────────────────────────

    private YieldPositionResponse calculatePosition(YieldPosition position) {
        long days = ChronoUnit.DAYS.between(position.getDepositedAt(), LocalDateTime.now());
        BigDecimal principal = position.getPrincipal();
        BigDecimal currentValue = computeCurrentValue(principal, days);

        BigDecimal dailyYield = principal
                .multiply(position.getInterestRate())
                .divide(DAYS_PER_YEAR, MathContext.DECIMAL64)
                .setScale(6, RoundingMode.HALF_UP);

        return new YieldPositionResponse(
                position.getId(),
                principal,
                currentValue,
                dailyYield,
                ANNUAL_YIELD_RATE,
                position.getStatus().name(),
                position.getDepositedAt()
        );
    }

    // Zinseszins: principal × (1 + rate/365)^days — BigDecimal DECIMAL64 für Präzision
    // package-private für Unit-Tests
    public BigDecimal computeCurrentValue(BigDecimal principal, long days) {
        BigDecimal dailyRate = ANNUAL_RATE_DECIMAL.divide(DAYS_PER_YEAR, MathContext.DECIMAL64);
        BigDecimal factor = BigDecimal.ONE.add(dailyRate, MathContext.DECIMAL64)
                .pow((int) Math.min(days, Integer.MAX_VALUE), MathContext.DECIMAL64);
        return principal.multiply(factor, MathContext.DECIMAL64).setScale(6, RoundingMode.HALF_UP);
    }

    private void saveAuditLog(UUID entityId, String entityType, String action,
                              String previousState, String newState, String userId) {
        AuditLog entry = new AuditLog();
        entry.setEntityType(entityType);
        entry.setEntityId(entityId);
        entry.setAction(action);
        entry.setPreviousState(previousState);
        entry.setNewState(newState);
        entry.setUserId(userId);
        auditLogRepository.save(entry);
    }
}
