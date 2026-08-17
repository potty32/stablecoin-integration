package de.atruvia.stablecoin.service.b2c;

import de.atruvia.stablecoin.client.ChainalysisClient;
import de.atruvia.stablecoin.client.CircleWalletClient;
import de.atruvia.stablecoin.client.dto.AddressScreenRequestDto;
import de.atruvia.stablecoin.client.dto.AddressScreenResponseDto;
import de.atruvia.stablecoin.client.dto.CircleTransferRequestDto;
import de.atruvia.stablecoin.client.dto.CircleTransferResponseDto;
import de.atruvia.stablecoin.dto.request.b2c.RemittanceRequest;
import de.atruvia.stablecoin.dto.response.RemittanceResponse;
import de.atruvia.stablecoin.entity.*;
import de.atruvia.stablecoin.exception.ComplianceBlockException;
import de.atruvia.stablecoin.exception.IdempotencyConflictException;
import de.atruvia.stablecoin.repository.AuditLogRepository;
import de.atruvia.stablecoin.repository.CustomerAccountRepository;
import de.atruvia.stablecoin.repository.StablecoinTransactionRepository;
import de.atruvia.stablecoin.service.revenue.RevenueService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class B2cRemittanceService {

    private static final Logger log = LoggerFactory.getLogger(B2cRemittanceService.class);

    private static final BigDecimal MXN_RATE = new BigDecimal("18.2");

    private static String currencyForCountry(String countryCode) {
        return switch (countryCode == null ? "" : countryCode.toUpperCase()) {
            case "MX" -> "MXN";
            case "PH" -> "PHP";
            case "IN" -> "INR";
            case "NG" -> "NGN";
            default   -> "USD";
        };
    }
    private static final String REMITTANCE_GATEWAY_WALLET = "0xB2CRemittanceFund00000000000000000000001";

    private final StablecoinTransactionRepository txRepository;
    private final CustomerAccountRepository accountRepository;
    private final AuditLogRepository auditLogRepository;
    private final CircleWalletClient circleWalletClient;
    private final ChainalysisClient chainalysisClient;
    private final RevenueService revenueService;

    @Value("${app.revenue.fee-b2c:0.50}")
    private BigDecimal feeB2c;

    public B2cRemittanceService(
            StablecoinTransactionRepository txRepository,
            CustomerAccountRepository accountRepository,
            AuditLogRepository auditLogRepository,
            CircleWalletClient circleWalletClient,
            ChainalysisClient chainalysisClient,
            RevenueService revenueService) {
        this.txRepository = txRepository;
        this.accountRepository = accountRepository;
        this.auditLogRepository = auditLogRepository;
        this.circleWalletClient = circleWalletClient;
        this.chainalysisClient = chainalysisClient;
        this.revenueService = revenueService;
    }

    @Transactional
    public RemittanceResponse send(String idempotencyKey, RemittanceRequest request, String userId) {
        txRepository.findByIdempotencyKey(idempotencyKey)
                .ifPresent(ex -> { throw new IdempotencyConflictException(ex.getId()); });

        CustomerAccount account = accountRepository.findByIban(request.sourceIban())
                .orElseThrow(() -> new NoSuchElementException("Account not found for IBAN: " + request.sourceIban()));

        AddressScreenResponseDto screening = chainalysisClient.screenAddress(
                new AddressScreenRequestDto(account.getWalletAddress(), "USDC", "MATIC", "outgoing"));
        if (!screening.approved()) {
            throw new ComplianceBlockException(account.getWalletAddress(), screening.riskScore());
        }

        StablecoinTransaction tx = new StablecoinTransaction();
        tx.setIdempotencyKey(idempotencyKey);
        tx.setCustomerAccount(account);
        tx.setType(TransactionType.REMITTANCE);
        tx.setCurrency(StablecoinCurrency.USDC);
        tx.setAmountFiat(request.amountEur());
        tx.setAmountStablecoin(request.amountEur().setScale(6, RoundingMode.HALF_UP));
        tx.setSourceWallet(account.getWalletAddress());
        tx.setDestinationWallet(REMITTANCE_GATEWAY_WALLET);
        tx.setTransactionFee(feeB2c);
        tx.setStatus(TransactionStatus.SUBMITTED);
        StablecoinTransaction savedTx = txRepository.save(tx);

        CircleTransferResponseDto circleResponse = circleWalletClient.initiateTransfer(
                new CircleTransferRequestDto(
                        idempotencyKey,
                        new CircleTransferRequestDto.Source("wallet", "B2C_MASTER_WALLET_ID"),
                        new CircleTransferRequestDto.Destination("blockchain", REMITTANCE_GATEWAY_WALLET, "MATIC"),
                        new CircleTransferRequestDto.Amount(request.amountEur().toPlainString(), "USDC")));

        RevenueService.RevenueCalculation revenue = revenueService.calculate(request.amountEur(), CustomerType.B2C);

        savedTx.setStatus(TransactionStatus.SETTLED);
        savedTx.setCircleTransactionId(circleResponse.id());
        savedTx.setSettledAt(LocalDateTime.now());
        savedTx.setGrossRevenue(revenue.grossRevenue());
        savedTx.setGasCost(revenue.gasCost());
        txRepository.save(savedTx);

        saveAuditLog(savedTx.getId(), "REMITTANCE_SENT", null,
                String.format("{\"status\":\"SETTLED\",\"amount\":\"%s\",\"recipient\":\"%s\",\"country\":\"%s\"}",
                        request.amountEur(), request.recipientName(), request.recipientCountry()),
                userId);

        log.info("[B2C-REMITTANCE] SETTLED tx={} amount={}EUR recipient={}",
                savedTx.getId(), request.amountEur(), request.recipientName());

        BigDecimal amountMxn = request.amountEur().multiply(MXN_RATE).setScale(2, RoundingMode.HALF_UP);
        String trackingCode = "ATR-" + savedTx.getId().toString().replace("-", "").substring(0, 8).toUpperCase();

        return new RemittanceResponse(
                savedTx.getId(),
                savedTx.getStatus().name(),
                feeB2c,
                amountMxn.toPlainString() + " " + currencyForCountry(request.recipientCountry()),
                "< 30 Sekunden",
                trackingCode
        );
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
