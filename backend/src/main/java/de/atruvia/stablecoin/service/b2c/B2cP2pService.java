package de.atruvia.stablecoin.service.b2c;

import de.atruvia.stablecoin.client.CircleWalletClient;
import de.atruvia.stablecoin.client.dto.CircleTransferRequestDto;
import de.atruvia.stablecoin.client.dto.CircleTransferResponseDto;
import de.atruvia.stablecoin.dto.request.b2c.P2pPhoneRequest;
import de.atruvia.stablecoin.dto.request.b2c.RegisterPhoneAliasRequest;
import de.atruvia.stablecoin.dto.response.TransactionResponse;
import de.atruvia.stablecoin.entity.*;
import de.atruvia.stablecoin.exception.IdempotencyConflictException;
import de.atruvia.stablecoin.repository.*;
import de.atruvia.stablecoin.service.revenue.RevenueService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
// G-14: PhoneHashService ersetzt statischen SHA-256-Salt
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class B2cP2pService {

    private static final Logger log = LoggerFactory.getLogger(B2cP2pService.class);

    private final StablecoinTransactionRepository txRepository;
    private final CustomerAccountRepository accountRepository;
    private final PhoneAliasRepository phoneAliasRepository;
    private final AuditLogRepository auditLogRepository;
    private final CircleWalletClient circleWalletClient;
    private final RevenueService revenueService;
    private final PhoneHashService phoneHashService;

    @Value("${app.revenue.fee-b2c:0.50}")
    private BigDecimal feeB2c;

    public B2cP2pService(
            StablecoinTransactionRepository txRepository,
            CustomerAccountRepository accountRepository,
            PhoneAliasRepository phoneAliasRepository,
            AuditLogRepository auditLogRepository,
            CircleWalletClient circleWalletClient,
            RevenueService revenueService,
            PhoneHashService phoneHashService) {
        this.txRepository = txRepository;
        this.accountRepository = accountRepository;
        this.phoneAliasRepository = phoneAliasRepository;
        this.auditLogRepository = auditLogRepository;
        this.circleWalletClient = circleWalletClient;
        this.revenueService = revenueService;
        this.phoneHashService = phoneHashService;
    }

    @Transactional
    public void registerPhoneAlias(RegisterPhoneAliasRequest request, String userId) {
        CustomerAccount account = accountRepository.findByIban(request.sourceIban())
                .orElseThrow(() -> new NoSuchElementException("Account not found for IBAN: " + request.sourceIban()));

        String hash = phoneHashService.hash(request.phoneNumber());

        PhoneAlias alias = new PhoneAlias();
        alias.setPhoneNumberHash(hash);
        alias.setWalletAddress(request.walletAddress());
        alias.setCustomerAccount(account);
        phoneAliasRepository.save(alias);

        saveAuditLog(account.getId(), "PhoneAlias", "PHONE_ALIAS_REGISTERED", userId,
                "Telefon-Alias registriert: iban=" + request.sourceIban() + ", walletAddress=" + request.walletAddress());

        log.info("[B2C-P2P] Phone alias registered for account={}", account.getCustomerId());
    }

    @Transactional
    public TransactionResponse sendToPhone(String idempotencyKey, P2pPhoneRequest request, String userId) {
        txRepository.findByIdempotencyKey(idempotencyKey)
                .ifPresent(ex -> { throw new IdempotencyConflictException(ex.getId()); });

        String recipientHash = phoneHashService.hash(request.recipientPhone());
        PhoneAlias recipientAlias = phoneAliasRepository.findByPhoneNumberHash(recipientHash)
                .orElseThrow(() -> new NoSuchElementException(
                        "No registered wallet for phone number: " + request.recipientPhone()));

        CustomerAccount senderAccount = accountRepository.findByIban(request.sourceIban())
                .orElseThrow(() -> new NoSuchElementException("Account not found for IBAN: " + request.sourceIban()));

        StablecoinTransaction tx = new StablecoinTransaction();
        tx.setIdempotencyKey(idempotencyKey);
        tx.setCustomerAccount(senderAccount);
        tx.setType(TransactionType.P2P);
        tx.setCurrency(StablecoinCurrency.USDC);
        tx.setAmountFiat(request.amountEur());
        tx.setAmountStablecoin(request.amountEur().setScale(6, RoundingMode.HALF_UP));
        tx.setSourceWallet(senderAccount.getWalletAddress());
        tx.setDestinationWallet(recipientAlias.getWalletAddress());
        tx.setTransactionFee(feeB2c);
        tx.setStatus(TransactionStatus.SUBMITTED);
        StablecoinTransaction savedTx = txRepository.save(tx);

        CircleTransferResponseDto circleResponse = circleWalletClient.initiateTransfer(
                new CircleTransferRequestDto(
                        idempotencyKey,
                        new CircleTransferRequestDto.Source("wallet", "B2C_MASTER_WALLET_ID"),
                        new CircleTransferRequestDto.Destination("blockchain", recipientAlias.getWalletAddress(), "MATIC"),
                        new CircleTransferRequestDto.Amount(request.amountEur().toPlainString(), "USDC")));

        RevenueService.RevenueCalculation revenue = revenueService.calculate(request.amountEur(), CustomerType.B2C);

        savedTx.setStatus(TransactionStatus.SETTLED);
        savedTx.setCircleTransactionId(circleResponse.id());
        savedTx.setSettledAt(LocalDateTime.now());
        savedTx.setGrossRevenue(revenue.grossRevenue());
        savedTx.setGasCost(revenue.gasCost());
        txRepository.save(savedTx);

        saveAuditLog(savedTx.getId(), "StablecoinTransaction", "P2P_SENT", userId,
                "P2P-Zahlung gesendet: amount=" + request.amountEur() + " EUR, to=" + recipientAlias.getWalletAddress());

        log.info("[B2C-P2P] SETTLED tx={} amount={}EUR to={}", savedTx.getId(), request.amountEur(), recipientAlias.getWalletAddress());

        return new TransactionResponse(
                savedTx.getId(), savedTx.getType(), savedTx.getStatus(),
                savedTx.getAmountFiat(), savedTx.getAmountStablecoin(), savedTx.getCurrency(),
                savedTx.getBlockchainHash(), savedTx.getGrossRevenue(), false,
                savedTx.getCreatedAt(), savedTx.getSettledAt(), Collections.emptyList()
        );
    }


    private void saveAuditLog(UUID entityId, String entityType, String action,
                              String userId, String details) {
        AuditLog entry = new AuditLog();
        entry.setEntityType(entityType);
        entry.setEntityId(entityId);
        entry.setAction(action);
        entry.setUserId(userId);
        entry.setDetails(details);
        auditLogRepository.save(entry);
    }
}
