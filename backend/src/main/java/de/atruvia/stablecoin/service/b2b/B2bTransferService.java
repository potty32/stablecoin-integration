package de.atruvia.stablecoin.service.b2b;

import de.atruvia.stablecoin.client.CircleWalletClient;
import de.atruvia.stablecoin.client.CoreBankingClient;
import de.atruvia.stablecoin.client.N8nWebhookClient;
import de.atruvia.stablecoin.client.TaurusCustodyClient;
import de.atruvia.stablecoin.client.dto.*;
import de.atruvia.stablecoin.dto.request.b2b.ApproveTransferRequest;
import de.atruvia.stablecoin.dto.request.b2b.InitiateTransferRequest;
import de.atruvia.stablecoin.dto.response.RateQuoteResponse;
import de.atruvia.stablecoin.dto.response.TransactionResponse;
import de.atruvia.stablecoin.dto.response.TransferPageResponse;
import de.atruvia.stablecoin.entity.*;
import de.atruvia.stablecoin.exception.ComplianceBlockException;
import de.atruvia.stablecoin.exception.IdempotencyConflictException;
import de.atruvia.stablecoin.exception.TaurusLimitExceededException;
import de.atruvia.stablecoin.repository.*;
import de.atruvia.stablecoin.service.compliance.ComplianceService;
import de.atruvia.stablecoin.service.revenue.RevenueService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@Service
public class B2bTransferService {

    private static final Logger log = LoggerFactory.getLogger(B2bTransferService.class);
    private static final BigDecimal BASE_RATE = new BigDecimal("1.000000");

    @Value("${app.revenue.fx-spread:0.0015}")
    private BigDecimal fxSpread;

    @Value("${app.rate-quote.validity-seconds:60}")
    private long rateQuoteValiditySeconds;

    // Self-Injection: @Transactional(REQUIRES_NEW)-Methoden müssen über den Proxy laufen
    @Lazy @Autowired
    private B2bTransferService self;

    private final StablecoinTransactionRepository txRepository;
    private final CustomerAccountRepository accountRepository;
    private final ApprovalWorkflowRepository approvalRepository;
    private final OutboxMessageRepository outboxRepository;
    private final AuditLogRepository auditLogRepository;
    private final RateQuoteRepository rateQuoteRepository;
    private final ComplianceService complianceService;
    private final RevenueService revenueService;
    private final CoreBankingClient coreBankingClient;
    private final CircleWalletClient circleWalletClient;
    private final TaurusCustodyClient taurusCustodyClient;
    private final N8nWebhookClient n8nWebhookClient;

    public B2bTransferService(
            StablecoinTransactionRepository txRepository,
            CustomerAccountRepository accountRepository,
            ApprovalWorkflowRepository approvalRepository,
            OutboxMessageRepository outboxRepository,
            AuditLogRepository auditLogRepository,
            RateQuoteRepository rateQuoteRepository,
            ComplianceService complianceService,
            RevenueService revenueService,
            CoreBankingClient coreBankingClient,
            CircleWalletClient circleWalletClient,
            TaurusCustodyClient taurusCustodyClient,
            N8nWebhookClient n8nWebhookClient) {
        this.txRepository = txRepository;
        this.accountRepository = accountRepository;
        this.approvalRepository = approvalRepository;
        this.outboxRepository = outboxRepository;
        this.auditLogRepository = auditLogRepository;
        this.rateQuoteRepository = rateQuoteRepository;
        this.complianceService = complianceService;
        this.revenueService = revenueService;
        this.coreBankingClient = coreBankingClient;
        this.circleWalletClient = circleWalletClient;
        this.taurusCustodyClient = taurusCustodyClient;
        this.n8nWebhookClient = n8nWebhookClient;
    }

    // ── Public API ────────────────────────────────────────────────────────────
    //
    // initiate() und approve() sind NICHT @Transactional.
    // Alle DB-Schreiboperationen laufen in eigenen committed Transaktionen via self.,
    // damit REQUIRES_NEW-Aufrufe in executeTransferFlow() die gespeicherten Daten lesen können.

    public TransactionResponse initiate(String idempotencyKey, InitiateTransferRequest request, String initiatorId) {
        txRepository.findByIdempotencyKey(idempotencyKey)
                .ifPresent(ex -> { throw new IdempotencyConflictException(ex.getId()); });

        // Schritt 1: TX + Outbox + Audit in eigener committed TX speichern
        InitResult init = self.persistInitialTransaction(idempotencyKey, request, initiatorId);

        if (init.requiresApproval()) {
            return init.response();
        }

        // Schritt 2: TX ist jetzt committed und für REQUIRES_NEW sichtbar
        StablecoinTransaction tx = txRepository.findById(init.txId()).orElseThrow();
        return executeTransferFlow(tx, initiatorId);
    }

    public TransactionResponse approve(UUID transactionId, ApproveTransferRequest request) {
        // Schritt 1: Approval committen (eigene TX)
        UUID txId = self.commitApproval(transactionId, request);

        // Schritt 2: TX laden und Flow ausführen
        StablecoinTransaction tx = txRepository.findById(txId).orElseThrow();
        return executeTransferFlow(tx, request.approverId());
    }

    @Transactional
    public TransactionResponse reject(UUID transactionId, ApproveTransferRequest request) {
        ApprovalWorkflow workflow = approvalRepository.findByTransactionId(transactionId)
                .orElseThrow(() -> new NoSuchElementException("Approval workflow not found: " + transactionId));
        if (workflow.getStatus() != ApprovalStatus.PENDING_APPROVAL) {
            throw new IllegalStateException("Not pending approval: " + workflow.getStatus());
        }
        workflow.setStatus(ApprovalStatus.REJECTED);
        approvalRepository.save(workflow);

        StablecoinTransaction tx = workflow.getTransaction();
        tx.setStatus(TransactionStatus.FAILED);
        tx.setFailureReason("Rejected by: " + request.approverId());
        txRepository.save(tx);

        saveAuditLog("StablecoinTransaction", tx.getId(), "REJECTED",
                "{\"status\":\"AWAITING_APPROVAL\"}",
                String.format("{\"status\":\"FAILED\",\"rejectedBy\":\"%s\"}", request.approverId()),
                request.approverId());
        return toResponse(tx, false);
    }

    public TransactionResponse getById(UUID id) {
        StablecoinTransaction tx = txRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Transaction not found: " + id));
        boolean requiresApproval = approvalRepository.findByTransactionId(id).isPresent();
        return toResponse(tx, requiresApproval);
    }

    @Transactional
    public RateQuoteResponse createRateQuote(BigDecimal amountEur, StablecoinCurrency currency, String userId) {
        CustomerAccount account = accountRepository.findAll().stream()
                .filter(a -> CustomerType.B2B.equals(a.getCustomerType()))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("No B2B account found"));

        BigDecimal rate = BASE_RATE.add(BASE_RATE.multiply(fxSpread)).setScale(8, RoundingMode.HALF_UP);
        LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(rateQuoteValiditySeconds);

        RateQuote quote = new RateQuote();
        quote.setCustomerAccount(account);
        quote.setSourceCurrency("EUR");
        quote.setTargetCurrency(currency.name());
        quote.setSourceAmount(amountEur);
        quote.setQuotedRate(rate);
        quote.setSpreadApplied(fxSpread);
        quote.setExpiresAt(expiresAt);
        rateQuoteRepository.save(quote);

        return new RateQuoteResponse(
                quote.getId(), amountEur,
                amountEur.multiply(rate).setScale(6, RoundingMode.HALF_UP).toPlainString(),
                rate, fxSpread.multiply(BigDecimal.valueOf(100)),
                new BigDecimal("2.50"), expiresAt, rateQuoteValiditySeconds
        );
    }

    // ── Committed Helper-Transaktionen ────────────────────────────────────────

    record InitResult(UUID txId, boolean requiresApproval, TransactionResponse response) {}

    @Transactional
    public InitResult persistInitialTransaction(String idempotencyKey, InitiateTransferRequest request, String initiatorId) {
        CustomerAccount account = accountRepository.findByIban(request.sourceIban())
                .orElseThrow(() -> new NoSuchElementException("Account not found: " + request.sourceIban()));

        BigDecimal effectiveRate = BASE_RATE.add(BASE_RATE.multiply(fxSpread));
        if (request.rateQuoteId() != null) {
            RateQuote quote = rateQuoteRepository.findByIdAndStatus(request.rateQuoteId(), QuoteStatus.ACTIVE)
                    .orElseThrow(() -> new IllegalArgumentException("Rate quote invalid: " + request.rateQuoteId()));
            if (quote.getExpiresAt().isBefore(LocalDateTime.now())) {
                quote.setStatus(QuoteStatus.EXPIRED);
                rateQuoteRepository.save(quote);
                throw new IllegalArgumentException("Rate quote expired");
            }
            effectiveRate = quote.getQuotedRate();
            quote.setStatus(QuoteStatus.USED);
            rateQuoteRepository.save(quote);
        }

        StablecoinTransaction tx = new StablecoinTransaction();
        tx.setIdempotencyKey(idempotencyKey);
        tx.setCustomerAccount(account);
        tx.setType(TransactionType.OUTBOUND);
        tx.setCurrency(request.currency());
        tx.setAmountFiat(request.amountEur());
        tx.setAmountStablecoin(request.amountEur().multiply(effectiveRate).setScale(6, RoundingMode.HALF_UP));
        tx.setFxRate(effectiveRate);
        tx.setFxSpread(fxSpread);
        tx.setDestinationWallet(request.destinationWallet());
        tx.setSourceWallet(account.getWalletAddress());
        tx.setStatus(TransactionStatus.PENDING);
        StablecoinTransaction savedTx = txRepository.save(tx);

        saveOutboxMessage(savedTx.getId(), "TRANSACTION_INITIATED",
                String.format("{\"amount\":\"%s\",\"currency\":\"%s\"}", request.amountEur(), request.currency()));
        saveAuditLog("StablecoinTransaction", savedTx.getId(), "CREATED", null,
                String.format("{\"status\":\"PENDING\",\"amount\":\"%s\"}", request.amountEur()), initiatorId);

        boolean requiresApproval = request.amountEur().compareTo(account.getTxLimitSingle()) > 0;
        if (requiresApproval) {
            ApprovalWorkflow workflow = new ApprovalWorkflow();
            workflow.setTransaction(savedTx);
            workflow.setInitiatorId(initiatorId);
            workflow.setExpiresAt(LocalDateTime.now().plusHours(24));
            approvalRepository.save(workflow);
            savedTx.setStatus(TransactionStatus.AWAITING_APPROVAL);
            txRepository.save(savedTx);
            saveAuditLog("StablecoinTransaction", savedTx.getId(), "APPROVAL_REQUIRED",
                    "{\"status\":\"PENDING\"}", "{\"status\":\"AWAITING_APPROVAL\"}", initiatorId);
            log.info("[B2B] tx={} AWAITING_APPROVAL ({}EUR > limit {}EUR)", savedTx.getId(), request.amountEur(), account.getTxLimitSingle());
        }
        return new InitResult(savedTx.getId(), requiresApproval, toResponse(savedTx, requiresApproval));
    }

    @Transactional
    public UUID commitApproval(UUID transactionId, ApproveTransferRequest request) {
        ApprovalWorkflow workflow = approvalRepository.findByTransactionId(transactionId)
                .orElseThrow(() -> new NoSuchElementException("Approval workflow not found: " + transactionId));
        if (workflow.getStatus() != ApprovalStatus.PENDING_APPROVAL) {
            throw new IllegalStateException("Not pending: " + workflow.getStatus());
        }
        if (workflow.getExpiresAt().isBefore(LocalDateTime.now())) {
            workflow.setStatus(ApprovalStatus.EXPIRED);
            approvalRepository.save(workflow);
            throw new IllegalStateException("Approval window expired");
        }
        workflow.setApproverId(request.approverId());
        workflow.setStatus(ApprovalStatus.APPROVED);
        workflow.setApprovedAt(LocalDateTime.now());
        approvalRepository.save(workflow);

        StablecoinTransaction tx = workflow.getTransaction();
        saveAuditLog("StablecoinTransaction", tx.getId(), "APPROVED",
                "{\"status\":\"AWAITING_APPROVAL\"}",
                String.format("{\"status\":\"APPROVED\",\"approver\":\"%s\"}", request.approverId()),
                request.approverId());
        log.info("[B2B] tx={} approved by {}", tx.getId(), request.approverId());
        return tx.getId();
    }

    // ── Kern-Orchestration (keine eigene TX — REQUIRES_NEW-Calls via self.) ──

    private TransactionResponse executeTransferFlow(StablecoinTransaction tx, String userId) {
        UUID txId = tx.getId();
        try {
            self.updateStatus(txId, TransactionStatus.COMPLIANCE_CHECK, userId);
            complianceService.screenAndAssert(tx.getDestinationWallet(), txId, userId);

            self.updateStatus(txId, TransactionStatus.PROCESSING, userId);

            HoldResponseDto hold = coreBankingClient.createHold(
                    tx.getCustomerAccount().getIban(),
                    new CreateHoldDto(tx.getAmountFiat(), "EUR", "STABLECOIN_OUTBOUND", txId.toString()));
            log.info("[B2B] hold={} tx={}", hold.holdId(), txId);

            TaurusTransactionResponseDto taurus = taurusCustodyClient.signAndSubmit(
                    new TaurusTransactionRequestDto(
                            tx.getCurrency() + "_POLYGON",
                            tx.getCustomerAccount().getWalletAddress(),
                            tx.getDestinationWallet(),
                            tx.getAmountStablecoin().toPlainString(),
                            new TaurusTransactionRequestDto.Metadata(txId.toString(), tx.getCustomerAccount().getCustomerId())));
            log.info("[B2B] taurus={} tx={}", taurus.id(), txId);

            CircleTransferResponseDto circleInit = circleWalletClient.initiateTransfer(
                    new CircleTransferRequestDto(
                            tx.getIdempotencyKey(),
                            new CircleTransferRequestDto.Source("wallet", "BANK_MASTER_WALLET_ID"),
                            new CircleTransferRequestDto.Destination("blockchain", tx.getDestinationWallet(), "MATIC"),
                            new CircleTransferRequestDto.Amount(tx.getAmountStablecoin().toPlainString(), tx.getCurrency().name())));
            self.persistCircleId(txId, circleInit.id());
            log.info("[B2B] circle={} tx={}", circleInit.id(), txId);

            CircleTransactionStatusDto settled = circleWalletClient.getTransactionStatus(circleInit.id());
            if (!"COMPLETE".equals(settled.status())) {
                throw new IllegalStateException("Circle not COMPLETE: " + settled.status());
            }
            log.info("[B2B] hash={} tx={}", settled.transactionHash(), txId);

            RevenueService.RevenueCalculation revenue = revenueService.calculate(
                    tx.getAmountFiat(), tx.getCustomerAccount().getCustomerType());
            coreBankingClient.createLedgerBooking(new LedgerBookingDto(
                    txId.toString(), tx.getCustomerAccount().getIban(),
                    List.of(
                            new LedgerBookingDto.CreditEntry("DE00ATRUVIA0001TRANSIT",
                                    tx.getAmountFiat().subtract(revenue.grossRevenue()), "Stablecoin Transit"),
                            new LedgerBookingDto.CreditEntry("DE00ATRUVIA0001ERTRAG",
                                    revenue.grossRevenue(), "Bruttoertrag Bank")),
                    tx.getAmountFiat(), "EUR", LocalDate.now()));

            self.settleTransaction(txId, settled.transactionHash(), revenue);

            StablecoinTransaction result = txRepository.findById(txId).orElseThrow();
            notifyN8n(result, revenue);
            saveOutboxMessage(txId, "TRANSACTION_SETTLED",
                    String.format("{\"hash\":\"%s\",\"revenue\":\"%s\"}", settled.transactionHash(), revenue.grossRevenue()));
            log.info("[B2B] SETTLED tx={} revenue={}EUR", txId, revenue.grossRevenue());
            return toResponse(result, false);

        } catch (ComplianceBlockException e) {
            self.markBlocked(txId, "COMPLIANCE_BLOCK: " + e.getMessage(), userId);
            throw e;
        } catch (TaurusLimitExceededException e) {
            self.markFailed(txId, "TAURUS_LIMIT: " + e.getMessage(), userId);
            throw e;
        } catch (Exception e) {
            log.error("[B2B] Transfer failed tx={}: {}", txId, e.getMessage(), e);
            self.markFailed(txId, e.getMessage(), userId);
            throw new IllegalStateException("Transfer failed: " + e.getMessage(), e);
        }
    }

    // ── @Transactional(REQUIRES_NEW) Status-Updater ───────────────────────────
    // Müssen via self. aufgerufen werden (Spring AOP Proxy-Requirement)

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateStatus(UUID txId, TransactionStatus newStatus, String userId) {
        StablecoinTransaction tx = txRepository.findById(txId)
                .orElseThrow(() -> new NoSuchElementException("TX not found for status update: " + txId));
        TransactionStatus old = tx.getStatus();
        tx.setStatus(newStatus);
        txRepository.save(tx);
        saveAuditLog("StablecoinTransaction", txId, "STATUS_CHANGED",
                String.format("{\"status\":\"%s\"}", old),
                String.format("{\"status\":\"%s\"}", newStatus), userId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persistCircleId(UUID txId, String circleId) {
        txRepository.findById(txId).ifPresent(tx -> {
            tx.setCircleTransactionId(circleId);
            txRepository.save(tx);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void settleTransaction(UUID txId, String blockchainHash, RevenueService.RevenueCalculation revenue) {
        txRepository.findById(txId).ifPresent(tx -> {
            tx.setStatus(TransactionStatus.SETTLED);
            tx.setBlockchainHash(blockchainHash);
            tx.setSettledAt(LocalDateTime.now());
            tx.setTransactionFee(revenue.transactionFee());
            tx.setGasCost(revenue.gasCost());
            tx.setGrossRevenue(revenue.grossRevenue());
            txRepository.save(tx);
        });
        saveAuditLog("StablecoinTransaction", txId, "SETTLED",
                "{\"status\":\"PROCESSING\"}",
                String.format("{\"status\":\"SETTLED\",\"hash\":\"%s\"}", blockchainHash), "SYSTEM");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(UUID txId, String reason, String userId) {
        txRepository.findById(txId).ifPresent(tx -> {
            tx.setStatus(TransactionStatus.FAILED);
            tx.setFailureReason(reason);
            txRepository.save(tx);
        });
        saveOutboxMessage(txId, "TRANSACTION_FAILED", String.format("{\"reason\":\"%s\"}", reason));
        saveAuditLog("StablecoinTransaction", txId, "FAILED",
                "{\"status\":\"PROCESSING\"}",
                String.format("{\"status\":\"FAILED\",\"reason\":\"%s\"}", reason), userId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markBlocked(UUID txId, String reason, String userId) {
        txRepository.findById(txId).ifPresent(tx -> {
            tx.setStatus(TransactionStatus.BLOCKED);
            tx.setFailureReason(reason);
            txRepository.save(tx);
        });
        saveOutboxMessage(txId, "TRANSACTION_BLOCKED", String.format("{\"reason\":\"%s\"}", reason));
        saveAuditLog("StablecoinTransaction", txId, "COMPLIANCE_BLOCKED",
                "{\"status\":\"COMPLIANCE_CHECK\"}",
                String.format("{\"status\":\"BLOCKED\",\"reason\":\"%s\"}", reason), userId);
    }

    // ── Private Helpers ────────────────────────────────────────────────────────

    private void notifyN8n(StablecoinTransaction tx, RevenueService.RevenueCalculation revenue) {
        try {
            n8nWebhookClient.notifySettlement(new SettlementNotificationDto(
                    tx.getId(), tx.getCustomerAccount().getCustomerId(), tx.getSettledAt(),
                    tx.getAmountFiat(), tx.getAmountStablecoin().toPlainString(), tx.getCurrency().name(),
                    tx.getBlockchainHash(), revenue.grossRevenue(),
                    new SettlementNotificationDto.RevenueBreakdown(
                            revenue.spreadAmount(), revenue.transactionFee(), revenue.gasCost())));
        } catch (Exception e) {
            log.warn("[B2B] n8n notification failed tx={}: {}", tx.getId(), e.getMessage());
        }
    }

    private void saveOutboxMessage(UUID transactionId, String eventType, String payload) {
        OutboxMessage msg = new OutboxMessage();
        msg.setTransactionId(transactionId);
        msg.setAggregateType("StablecoinTransaction");
        msg.setEventType(eventType);
        msg.setPayload(payload);
        outboxRepository.save(msg);
    }

    private void saveAuditLog(String entityType, UUID entityId, String action,
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

    private TransactionResponse toResponse(StablecoinTransaction tx, boolean requiresApproval) {
        return new TransactionResponse(
                tx.getId(), tx.getType(), tx.getStatus(),
                tx.getAmountFiat(), tx.getAmountStablecoin(), tx.getCurrency(),
                tx.getBlockchainHash(), tx.getGrossRevenue(), requiresApproval,
                tx.getCreatedAt(), tx.getSettledAt(), buildTimeline(tx.getId())
        );
    }

    private static final Pattern STATUS_PATTERN = Pattern.compile("\"status\"\\s*:\\s*\"([^\"]+)\"");

    private List<TransactionResponse.TimelineEntry> buildTimeline(UUID txId) {
        List<de.atruvia.stablecoin.entity.AuditLog> entries =
                auditLogRepository.findByEntityTypeAndEntityIdOrderByTimestampAsc("StablecoinTransaction", txId);

        Map<TransactionStatus, LocalDateTime> seen = new LinkedHashMap<>();
        for (de.atruvia.stablecoin.entity.AuditLog entry : entries) {
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

    public TransferPageResponse listTransfers(String userId, TransactionStatus statusFilter, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<StablecoinTransaction> txPage = accountRepository.findByCustomerId(userId)
                .map(account -> statusFilter != null
                        ? txRepository.findByCustomerAccountIdAndStatus(account.getId(), statusFilter, pageable)
                        : txRepository.findByCustomerAccountId(account.getId(), pageable))
                .orElseGet(() -> statusFilter != null
                        ? txRepository.findByStatus(statusFilter, pageable)
                        : txRepository.findAll(pageable));

        List<TransactionResponse> content = txPage.getContent().stream()
                .map(tx -> toResponse(tx, approvalRepository.findByTransactionId(tx.getId()).isPresent()))
                .toList();

        return new TransferPageResponse(content, txPage.getTotalElements(), txPage.getTotalPages(), page, size);
    }
}
