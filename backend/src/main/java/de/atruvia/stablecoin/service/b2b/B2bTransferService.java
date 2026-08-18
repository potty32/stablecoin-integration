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
import de.atruvia.stablecoin.entity.AddressStatus;
import de.atruvia.stablecoin.entity.InstitutionalAddressStatus;
import de.atruvia.stablecoin.exception.ComplianceBlockException;
import de.atruvia.stablecoin.exception.IdempotencyConflictException;
import de.atruvia.stablecoin.exception.TaurusLimitExceededException;
import de.atruvia.stablecoin.repository.*;
import de.atruvia.stablecoin.config.TenantContext;
import de.atruvia.stablecoin.service.compliance.ComplianceService;
import de.atruvia.stablecoin.service.fx.FxRateService;
import de.atruvia.stablecoin.exception.SlippageExceededException;
import de.atruvia.stablecoin.service.revenue.RevenueService;
import de.atruvia.stablecoin.service.b2b.TenantSettingsService;
import de.atruvia.stablecoin.entity.TenantSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static de.atruvia.stablecoin.entity.TransactionStatus.*;
import static java.util.Map.entry;

@Service
public class B2bTransferService {

    private static final Logger log = LoggerFactory.getLogger(B2bTransferService.class);

    // ── State Machine: erlaubte Zustandsübergänge ─────────────────────────────
    // Terminale Zustände (REDEEMED, REJECTED, EXPIRED, FAILED) haben keinen Eintrag
    // → getOrDefault liefert leere Menge → alle weiteren Übergänge blockiert
    private static final Map<TransactionStatus, EnumSet<TransactionStatus>> ALLOWED =
        Map.ofEntries(
            // ── Outbound-Pfad ──────────────────────────────────────────────────
            entry(CREATED,              EnumSet.of(PENDING_APPROVAL, COMPLIANCE_CHECKED, INCOMING, FAILED, RETURNED)),
            entry(PENDING_APPROVAL,     EnumSet.of(APPROVED, REJECTED, EXPIRED, FAILED)),
            entry(APPROVED,             EnumSet.of(COMPLIANCE_CHECKED, FAILED)),
            entry(COMPLIANCE_CHECKED,   EnumSet.of(FUNDS_HELD, FAILED)),
            entry(FUNDS_HELD,           EnumSet.of(SUBMITTED, FAILED)),
            entry(SUBMITTED,            EnumSet.of(SETTLED, FAILED)),
            entry(SETTLED,              EnumSet.of(REDEEMED, FAILED)),
            // ── Inbound-Pfad ───────────────────────────────────────────────────
            entry(INCOMING,             EnumSet.of(COMPLIANCE_PENDING, FAILED)),
            entry(COMPLIANCE_PENDING,   EnumSet.of(COMPLIANCE_APPROVED, COMPLIANCE_REJECTED, FAILED)),
            entry(COMPLIANCE_APPROVED,  EnumSet.of(SETTLED, FAILED)),
            entry(COMPLIANCE_REJECTED,  EnumSet.of(FAILED)),
            // ── Enterprise: INBOUND_RETURN und UNASSIGNED ──────────────────────
            // INBOUND_RETURN: CREATED → RETURNED (direkt nach Circle-Transfer) oder FAILED
            // CREATED → RETURNED ist über den normalen CREATED-Eintrag oben erreichbar:
            // CREATED bereits in der Map → ergänze RETURNED als gültiges Ziel
            entry(UNASSIGNED,           EnumSet.of(SETTLED, FAILED))
        );

    private final FxRateService fxRateService;

    @Value("${app.revenue.fx-spread:0.0015}")
    private BigDecimal fxSpread;

    @Value("${app.rate-quote.validity-seconds:60}")
    private long rateQuoteValiditySeconds;

    @Value("${app.security.dev-mode:false}")
    private boolean devMode;

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
    private final AddressBookRepository addressBookRepository;
    private final InstitutionalAddressBookRepository institutionalAddressBookRepository;
    private final TenantSettingsService tenantSettingsService;

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
            N8nWebhookClient n8nWebhookClient,
            AddressBookRepository addressBookRepository,
            InstitutionalAddressBookRepository institutionalAddressBookRepository,
            FxRateService fxRateService,
            TenantSettingsService tenantSettingsService) {
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
        this.addressBookRepository = addressBookRepository;
        this.institutionalAddressBookRepository = institutionalAddressBookRepository;
        this.fxRateService = fxRateService;
        this.tenantSettingsService = tenantSettingsService;
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    public TransactionResponse initiate(String idempotencyKey, InitiateTransferRequest request, String initiatorId) {
        // Idempotenz-Check läuft atomar in persistInitialTransaction() [@Transactional]
        // DB-UNIQUE-Constraint auf idempotency_key ist die zweite Sicherheitslinie
        InitResult init = self.persistInitialTransaction(idempotencyKey, request, initiatorId);

        if (init.requiresApproval()) {
            return init.response();
        }

        StablecoinTransaction tx = txRepository.findById(init.txId()).orElseThrow();
        return executeTransferFlow(tx, initiatorId);
    }

    public TransactionResponse approve(UUID transactionId, ApproveTransferRequest request) {
        UUID txId = self.commitApproval(transactionId, request);
        StablecoinTransaction tx = txRepository.findById(txId).orElseThrow();
        return executeTransferFlow(tx, request.approverId());
    }

    public TransactionResponse reject(UUID transactionId, ApproveTransferRequest request) {
        ApprovalWorkflow workflow = approvalRepository.findByTransactionId(transactionId)
                .orElseThrow(() -> new NoSuchElementException("Approval workflow not found: " + transactionId));

        if (workflow.getStatus() != ApprovalStatus.PENDING_APPROVAL) {
            throw new IllegalStateException("Not pending approval: " + workflow.getStatus());
        }
        if (!devMode && workflow.getInitiatorId().equals(request.approverId())) {
            throw new IllegalStateException("Self-approval not allowed: initiator and approver must be different users");
        }

        UUID txId = workflow.getTransaction().getId();
        self.commitWorkflowRejection(transactionId, request.approverId());
        self.transitionToRejected(txId, "Rejected by: " + request.approverId(), request.approverId());

        StablecoinTransaction tx = txRepository.findById(txId).orElseThrow();
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

        BigDecimal baseRate = fxRateService.getBaseRate(currency);
        BigDecimal rate = baseRate.add(baseRate.multiply(fxSpread)).setScale(8, RoundingMode.HALF_UP);
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

    // ── State Machine — zentrale, thread-sichere Methode ─────────────────────
    // Alle REQUIRES_NEW-Methoden müssen via self. aufgerufen werden (Spring AOP Proxy)

    /**
     * Zentrale State-Machine-Methode. Validiert den Zustandsübergang und blockiert
     * ungültige Übergänge mit IllegalStateException. Bei Übergang nach FAILED aus
     * FUNDS_HELD oder SUBMITTED wird der Hold automatisch freigegeben (Auto-Rollback).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void transitionTo(UUID txId, TransactionStatus targetStatus, String userId) {
        StablecoinTransaction tx = txRepository.findByIdWithLock(txId)
                .orElseThrow(() -> new NoSuchElementException("TX not found: " + txId));
        TransactionStatus current = tx.getStatus();
        validateTransition(current, targetStatus);

        if (targetStatus == FAILED &&
                EnumSet.of(FUNDS_HELD, SUBMITTED).contains(current) &&
                tx.getHoldId() != null) {
            coreBankingClient.releaseHold(tx.getHoldId());
            log.warn("[STATE-MACHINE] Auto-released hold={} on FAILED from {}", tx.getHoldId(), current);
        }

        tx.setStatus(targetStatus);
        txRepository.save(tx);
        saveTransitionLog(txId, current, targetStatus, userId, "Statuswechsel: " + current + " → " + targetStatus);
        log.info("[STATE-MACHINE] tx={} {} → {}", txId, current, targetStatus);
    }

    /** Übergang nach FUNDS_HELD — speichert zusätzlich die holdId für späteren Auto-Release. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void transitionToFundsHeld(UUID txId, String holdId, String userId) {
        StablecoinTransaction tx = txRepository.findByIdWithLock(txId)
                .orElseThrow(() -> new NoSuchElementException("TX not found: " + txId));
        validateTransition(tx.getStatus(), FUNDS_HELD);
        tx.setStatus(FUNDS_HELD);
        tx.setHoldId(holdId);
        txRepository.save(tx);
        // Recovery-Trigger: SUBMIT_TO_BLOCKCHAIN wird in derselben REQUIRES_NEW-TX committed.
        // Wenn das System nach FUNDS_HELD crasht, liest der OutboxProcessor diese Nachricht
        // beim Neustart und finalisiert die TX kontrolliert (poll Circle / release hold).
        saveOutboxMessage(txId, "SUBMIT_TO_BLOCKCHAIN",
                String.format("{\"txId\":\"%s\",\"holdId\":\"%s\"}", txId, holdId));
        saveTransitionLog(txId, COMPLIANCE_CHECKED, FUNDS_HELD, userId,
                "EUR-Hold angelegt: holdId=" + holdId);
        log.info("[STATE-MACHINE] tx={} {} → FUNDS_HELD holdId={}", txId, COMPLIANCE_CHECKED, holdId);
    }

    /** Übergang nach FAILED — speichert Grund, löst Hold automatisch aus (wenn vorhanden). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void transitionToFailed(UUID txId, String reason, String userId) {
        StablecoinTransaction tx = txRepository.findByIdWithLock(txId)
                .orElseThrow(() -> new NoSuchElementException("TX not found: " + txId));
        TransactionStatus current = tx.getStatus();
        validateTransition(current, FAILED);

        if (EnumSet.of(FUNDS_HELD, SUBMITTED).contains(current) && tx.getHoldId() != null) {
            coreBankingClient.releaseHold(tx.getHoldId());
            log.warn("[STATE-MACHINE] Auto-released hold={} on FAILED from {}", tx.getHoldId(), current);
        }

        // G-01: Storno-Buchung wenn Ledger bereits verbucht war
        if (tx.getLedgerBookingReference() != null) {
            try {
                coreBankingClient.reverseBooking(tx.getLedgerBookingReference(),
                        "Storno Transaktionsabbruch: " + reason);
                log.warn("[STATE-MACHINE] Storno-Buchung tx={} originalRef={}", txId, tx.getLedgerBookingReference());
            } catch (Exception e) {
                log.error("[STATE-MACHINE] Storno-Buchung FEHLGESCHLAGEN tx={} ref={}: {}",
                        txId, tx.getLedgerBookingReference(), e.getMessage());
                // Kein Re-throw: FAILED-Status wird trotzdem gesetzt (manueller Storno nötig)
            }
        }

        tx.setStatus(FAILED);
        tx.setFailureReason(reason);
        txRepository.save(tx);
        saveOutboxMessage(txId, "TRANSACTION_FAILED", String.format("{\"reason\":\"%s\"}", reason));
        saveTransitionLog(txId, current, FAILED, userId, "Abbruch aus " + current + ": " + reason);
        log.warn("[STATE-MACHINE] tx={} {} → FAILED reason={}", txId, current, reason);
    }

    /** Übergang nach REJECTED — speichert Ablehnungsgrund. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void transitionToRejected(UUID txId, String reason, String userId) {
        StablecoinTransaction tx = txRepository.findByIdWithLock(txId)
                .orElseThrow(() -> new NoSuchElementException("TX not found: " + txId));
        TransactionStatus current = tx.getStatus();
        validateTransition(current, REJECTED);
        tx.setStatus(REJECTED);
        tx.setFailureReason(reason);
        txRepository.save(tx);
        saveTransitionLog(txId, current, REJECTED, userId, "Abgelehnt: " + reason);
        log.info("[STATE-MACHINE] tx={} {} → REJECTED reason={}", txId, current, reason);
    }

    /** Übergang nach SETTLED — speichert Blockchain-Hash und Revenue-Daten. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void settleTransaction(UUID txId, String blockchainHash, RevenueService.RevenueCalculation revenue) {
        StablecoinTransaction tx = txRepository.findByIdWithLock(txId)
                .orElseThrow(() -> new NoSuchElementException("TX not found: " + txId));
        validateTransition(tx.getStatus(), SETTLED);
        tx.setStatus(SETTLED);
        tx.setBlockchainHash(blockchainHash);
        tx.setSettledAt(LocalDateTime.now());
        tx.setTransactionFee(revenue.transactionFee());
        tx.setGasCost(revenue.gasCost());
        tx.setGrossRevenue(revenue.grossRevenue());
        txRepository.save(tx);
        saveTransitionLog(txId, SUBMITTED, SETTLED, "SYSTEM",
                "Settlement abgeschlossen: blockchainHash=" + blockchainHash);
        log.info("[STATE-MACHINE] tx={} SUBMITTED → SETTLED hash={}", txId, blockchainHash);
    }

    // ── Committed Helper-Transaktionen ────────────────────────────────────────

    record InitResult(UUID txId, boolean requiresApproval, TransactionResponse response) {}

    @Transactional
    public InitResult persistInitialTransaction(String idempotencyKey, InitiateTransferRequest request, String initiatorId) {
        // ATOMARE Idempotenz: Check und Insert in derselben @Transactional
        // DB-UNIQUE-Constraint auf idempotency_key fängt Race Conditions auf DB-Ebene ab
        txRepository.findByIdempotencyKey(idempotencyKey)
                .ifPresent(existing -> { throw new IdempotencyConflictException(existing.getId()); });

        CustomerAccount account = accountRepository.findByIban(request.sourceIban())
                .orElseThrow(() -> new NoSuchElementException("Account not found: " + request.sourceIban()));

        // Whitelist-Check: Kunden-Adressbuch ODER institutionelle Whitelist (MiCA/FATF)
        boolean inCustomerWhitelist = addressBookRepository
                .findByCustomerAccountIdAndWalletAddressAndStatus(
                        account.getId(), request.destinationWallet(), AddressStatus.ACTIVE)
                .isPresent();
        boolean inInstitutionalWhitelist = institutionalAddressBookRepository
                .findByWalletAddressAndStatus(request.destinationWallet(), InstitutionalAddressStatus.ACTIVE)
                .isPresent();
        if (!inCustomerWhitelist && !inInstitutionalWhitelist) {
            log.warn("[B2B] Whitelist block: wallet={} account={}", request.destinationWallet(), account.getCustomerId());
            throw new ComplianceBlockException(request.destinationWallet(), "NOT_WHITELISTED");
        }

        // G-03: Mandantenspezifische Gebühren + G-01: Gross-Debit-Berechnung
        TenantSettings settings = tenantSettingsService.get(TenantContext.get());
        BigDecimal tenantSpread = CustomerType.B2B.equals(account.getCustomerType())
                ? settings.getFxSpreadB2b() : settings.getFxSpreadB2c();
        BigDecimal tenantFee = CustomerType.B2B.equals(account.getCustomerType())
                ? settings.getFeeFlatB2bEur() : settings.getFeeFlatB2cEur();

        BigDecimal baseRate = fxRateService.getBaseRate(request.currency());
        BigDecimal effectiveRate = baseRate.add(baseRate.multiply(tenantSpread));
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
        // G-01: Gross-Debit = Sendebetrag + Flat-Gebühr + FX-Spread-Betrag
        BigDecimal spreadAmount = request.amountEur().multiply(tenantSpread).setScale(6, RoundingMode.HALF_UP);
        BigDecimal grossDebit   = request.amountEur().add(tenantFee).add(spreadAmount).setScale(6, RoundingMode.HALF_UP);

        tx.setAmountFiat(request.amountEur());
        tx.setAmountStablecoin(request.amountEur().multiply(effectiveRate).setScale(6, RoundingMode.HALF_UP));
        tx.setFxRate(effectiveRate);
        tx.setFxSpread(tenantSpread);
        tx.setGrossDebit(grossDebit);
        tx.setFeeAmount(tenantFee);
        tx.setSlippageToleranceBps(settings.getSlippageToleranceBps());
        tx.setDestinationWallet(request.destinationWallet());
        tx.setSourceWallet(account.getWalletAddress());
        // Status wird via @PrePersist auf CREATED gesetzt
        StablecoinTransaction savedTx = txRepository.save(tx);

        saveOutboxMessage(savedTx.getId(), "TRANSACTION_INITIATED",
                String.format("{\"amount\":\"%s\",\"currency\":\"%s\"}", request.amountEur(), request.currency()));
        saveTransitionLog(savedTx.getId(), null, CREATED, initiatorId,
                "Transfer initiiert: " + request.amountEur() + " EUR " + request.currency());

        // G-03: Vier-Augen-Schwelle aus TenantSettings (statt statischem account.txLimitSingle)
        boolean requiresApproval = request.amountEur().compareTo(settings.getApprovalThresholdB2b()) > 0;
        if (requiresApproval) {
            ApprovalWorkflow workflow = new ApprovalWorkflow();
            workflow.setTransaction(savedTx);
            workflow.setInitiatorId(initiatorId);
            workflow.setExpiresAt(LocalDateTime.now().plusHours(24));
            approvalRepository.save(workflow);
            savedTx.setStatus(PENDING_APPROVAL);
            txRepository.save(savedTx);
            saveTransitionLog(savedTx.getId(), CREATED, PENDING_APPROVAL, initiatorId,
                    "Vier-Augen-Freigabe erforderlich (Betrag: " + request.amountEur() + " EUR > Limit)");
            log.info("[B2B] tx={} PENDING_APPROVAL ({}EUR > limit {}EUR)",
                    savedTx.getId(), request.amountEur(), account.getTxLimitSingle());
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
        if (!devMode && workflow.getInitiatorId().equals(request.approverId())) {
            throw new IllegalStateException("Self-approval not allowed: initiator and approver must be different users");
        }

        if (workflow.getExpiresAt().isBefore(LocalDateTime.now())) {
            // REQUIRES_NEW: committed auch wenn äußere TX rollt zurück
            self.markWorkflowExpired(transactionId);
            self.transitionTo(workflow.getTransaction().getId(), EXPIRED, request.approverId());
            throw new IllegalStateException("Approval window expired");
        }

        workflow.setApproverId(request.approverId());
        workflow.setStatus(ApprovalStatus.APPROVED);
        workflow.setApprovedAt(LocalDateTime.now());
        approvalRepository.save(workflow);

        UUID txId = workflow.getTransaction().getId();
        self.transitionTo(txId, APPROVED, request.approverId());
        log.info("[B2B] tx={} approved by {}", txId, request.approverId());
        return txId;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markWorkflowExpired(UUID transactionId) {
        ApprovalWorkflow workflow = approvalRepository.findByTransactionId(transactionId).orElseThrow();
        workflow.setStatus(ApprovalStatus.EXPIRED);
        approvalRepository.save(workflow);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void commitWorkflowRejection(UUID transactionId, String approverId) {
        ApprovalWorkflow workflow = approvalRepository.findByTransactionId(transactionId).orElseThrow();
        workflow.setStatus(ApprovalStatus.REJECTED);
        workflow.setApproverId(approverId);
        approvalRepository.save(workflow);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persistCircleId(UUID txId, String circleId) {
        txRepository.findById(txId).ifPresent(tx -> {
            tx.setCircleTransactionId(circleId);
            txRepository.save(tx);
        });
    }

    /** G-01: Speichert Kernbank-Buchungsreferenz für spätere Storno-Möglichkeit. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persistLedgerRef(UUID txId, String bookingReference) {
        txRepository.findById(txId).ifPresent(tx -> {
            tx.setLedgerBookingReference(bookingReference);
            txRepository.save(tx);
        });
    }

    // ── @Retry + @CircuitBreaker Wrapper (via self für AOP-Proxy) ─────────────

    @io.github.resilience4j.retry.annotation.Retry(name = "taurus-custody")
    @io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker(name = "taurus-custody", fallbackMethod = "taurusSubmitFallback")
    public TaurusTransactionResponseDto submitToTaurus(StablecoinTransaction tx, String userId) {
        return taurusCustodyClient.signAndSubmit(new TaurusTransactionRequestDto(
                tx.getCurrency() + "_POLYGON",
                tx.getCustomerAccount().getWalletAddress(),
                tx.getDestinationWallet(),
                tx.getAmountStablecoin().toPlainString(),
                new TaurusTransactionRequestDto.Metadata(tx.getId().toString(), tx.getCustomerAccount().getCustomerId())));
    }

    public TaurusTransactionResponseDto taurusSubmitFallback(StablecoinTransaction tx, String userId, Throwable ex) {
        log.error("[CB/RETRY] Taurus unavailable for tx={}: {}", tx.getId(), ex.getMessage());
        self.transitionToFailed(tx.getId(), "TAURUS_UNAVAILABLE: " + ex.getMessage(), userId);
        throw new IllegalStateException("Taurus nicht erreichbar — TX abgebrochen, Hold freigegeben", ex);
    }

    @io.github.resilience4j.retry.annotation.Retry(name = "circle-wallet")
    @io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker(name = "circle-wallet", fallbackMethod = "circleTransferFallback")
    public CircleTransferResponseDto submitToCircle(StablecoinTransaction tx, String userId) {
        return circleWalletClient.initiateTransfer(new CircleTransferRequestDto(
                tx.getIdempotencyKey(),
                new CircleTransferRequestDto.Source("wallet", "BANK_MASTER_WALLET_ID"),
                new CircleTransferRequestDto.Destination("blockchain", tx.getDestinationWallet(), "MATIC"),
                new CircleTransferRequestDto.Amount(tx.getAmountStablecoin().toPlainString(), tx.getCurrency().name())));
    }

    public CircleTransferResponseDto circleTransferFallback(StablecoinTransaction tx, String userId, Throwable ex) {
        log.error("[CB/RETRY] Circle unavailable for tx={}: {}", tx.getId(), ex.getMessage());
        self.transitionToFailed(tx.getId(), "CIRCLE_UNAVAILABLE: " + ex.getMessage(), userId);
        throw new IllegalStateException("Circle nicht erreichbar — TX abgebrochen, Hold freigegeben", ex);
    }

    @io.github.resilience4j.retry.annotation.Retry(name = "circle-wallet")
    @io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker(name = "circle-wallet", fallbackMethod = "pollCircleStatusFallback")
    public CircleTransactionStatusDto pollCircleStatus(String circleId, UUID txId, String userId) {
        return circleWalletClient.getTransactionStatus(circleId);
    }

    public CircleTransactionStatusDto pollCircleStatusFallback(String circleId, UUID txId, String userId, Throwable ex) {
        log.error("[CB/RETRY] Circle poll unavailable for tx={} circleId={}: {}", txId, circleId, ex.getMessage());
        self.transitionToFailed(txId, "CIRCLE_POLL_UNAVAILABLE: " + ex.getMessage(), userId);
        throw new IllegalStateException("Circle Status-Abfrage nicht erreichbar — TX abgebrochen, Hold freigegeben", ex);
    }

    // ── Kern-Orchestration ────────────────────────────────────────────────────

    private TransactionResponse executeTransferFlow(StablecoinTransaction tx, String userId) {
        UUID txId = tx.getId();
        try {
            self.transitionTo(txId, COMPLIANCE_CHECKED, userId);
            complianceService.screenAndAssert(tx.getDestinationWallet(), txId, userId, "outgoing");

            // G-06: Slippage-Schutz — Kursabweichung seit Auftragserfassung prüfen
            if (tx.getFxRate() != null && tx.getCurrency() != StablecoinCurrency.EURC) {
                BigDecimal rateAtCreation = tx.getFxRate();
                BigDecimal currentRate    = fxRateService.getBaseRate(tx.getCurrency());
                int toleranceBps = tx.getSlippageToleranceBps() != null
                        ? tx.getSlippageToleranceBps() : 100;
                if (currentRate.compareTo(BigDecimal.ZERO) > 0) {
                    int actualBps = rateAtCreation.subtract(currentRate).abs()
                            .divide(rateAtCreation, 8, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(10000))
                            .intValue();
                    if (actualBps > toleranceBps) {
                        throw new SlippageExceededException(actualBps, toleranceBps);
                    }
                }
            }

            // G-01: Hold auf Bruttobetrag (Sendebetrag + Gebühr + Spread)
            BigDecimal holdAmount = tx.getGrossDebit() != null ? tx.getGrossDebit() : tx.getAmountFiat();
            HoldResponseDto hold = coreBankingClient.createHold(
                    tx.getCustomerAccount().getIban(),
                    new CreateHoldDto(holdAmount, "EUR", "STABLECOIN_OUTBOUND_GROSS", txId.toString()));
            self.transitionToFundsHeld(txId, hold.holdId(), userId);
            log.info("[B2B] hold={} tx={}", hold.holdId(), txId);

            // Taurus: @Retry(2x) + @CircuitBreaker — bei Ausfall: FAILED + Hold freigeben
            TaurusTransactionResponseDto taurus = self.submitToTaurus(tx, userId);
            log.info("[B2B] taurus={} tx={}", taurus.id(), txId);

            self.transitionTo(txId, SUBMITTED, userId);

            // Circle: @Retry(3x) + @CircuitBreaker — idempotency-key schützt vor Doppelbuchung
            CircleTransferResponseDto circleInit = self.submitToCircle(tx, userId);
            self.persistCircleId(txId, circleInit.id());
            log.info("[B2B] circle={} tx={}", circleInit.id(), txId);

            // Circle Status pollen: @Retry(3x) + @CircuitBreaker
            CircleTransactionStatusDto settled = self.pollCircleStatus(circleInit.id(), txId, userId);
            if (!"COMPLETE".equals(settled.status())) {
                throw new IllegalStateException("Circle not COMPLETE: " + settled.status());
            }
            log.info("[B2B] hash={} tx={}", settled.transactionHash(), txId);

            TenantSettings txSettings = tenantSettingsService.get(TenantContext.get());
            RevenueService.RevenueCalculation revenue = revenueService.calculate(
                    tx.getAmountFiat(), tx.getCustomerAccount().getCustomerType(), txSettings);

            // G-01: Brutto-Modell — Transit erhält vollen Sendebetrag, Ertragskonto die Gebühren
            BigDecimal totalGross  = tx.getGrossDebit() != null ? tx.getGrossDebit() : tx.getAmountFiat();
            BigDecimal revenueAmt  = totalGross.subtract(tx.getAmountFiat()).max(revenue.grossRevenue());
            BookingResponseDto booking = coreBankingClient.createLedgerBooking(new LedgerBookingDto(
                    txId.toString(), tx.getCustomerAccount().getIban(),
                    List.of(
                            new LedgerBookingDto.CreditEntry("DE00ATRUVIA0001TRANSIT",
                                    tx.getAmountFiat(), "Stablecoin Transit (Sendebetrag netto)"),
                            new LedgerBookingDto.CreditEntry("DE00ATRUVIA0001ERTRAG",
                                    revenueAmt, "Bruttoertrag Bank (Gebühr+Spread)")),
                    totalGross, "EUR", LocalDate.now()));

            // G-01: Buchungsreferenz sichern (ermöglicht Storno bei nachfolgendem FAILED)
            self.persistLedgerRef(txId, booking.bookingId());

            self.settleTransaction(txId, settled.transactionHash(), revenue);

            StablecoinTransaction result = txRepository.findById(txId).orElseThrow();
            notifyN8n(result, revenue);
            saveOutboxMessage(txId, "TRANSACTION_SETTLED",
                    String.format("{\"hash\":\"%s\",\"revenue\":\"%s\"}", settled.transactionHash(), revenue.grossRevenue()));
            log.info("[B2B] SETTLED tx={} revenue={}EUR", txId, revenue.grossRevenue());
            return toResponse(result, false);

        } catch (ComplianceBlockException e) {
            self.transitionToFailed(txId, "COMPLIANCE_BLOCK: " + e.getMessage(), userId);
            throw e;
        } catch (TaurusLimitExceededException e) {
            self.transitionToFailed(txId, "TAURUS_LIMIT: " + e.getMessage(), userId);
            throw e;
        } catch (Exception e) {
            log.error("[B2B] Transfer failed tx={}: {}", txId, e.getMessage(), e);
            self.transitionToFailed(txId, e.getMessage(), userId);
            throw new IllegalStateException("Transfer failed: " + e.getMessage(), e);
        }
    }

    // ── Private Helpers ────────────────────────────────────────────────────────

    /** Validiert einen Zustandsübergang gegen die ALLOWED-Map. Wirft IllegalStateException bei ungültigem Übergang. */
    private void validateTransition(TransactionStatus current, TransactionStatus target) {
        EnumSet<TransactionStatus> allowed = ALLOWED.getOrDefault(current, EnumSet.noneOf(TransactionStatus.class));
        if (!allowed.contains(target)) {
            throw new IllegalStateException(
                    String.format("Ungültiger Statusübergang: %s → %s (erlaubt: %s)", current, target, allowed));
        }
    }

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

    private void saveTransitionLog(UUID txId, TransactionStatus from, TransactionStatus to,
                                   String userId, String details) {
        AuditLog entry = new AuditLog();
        entry.setTransactionId(txId);
        entry.setEntityType("StablecoinTransaction");
        entry.setEntityId(txId);
        entry.setFromStatus(from);
        entry.setToStatus(to);
        entry.setAction("STATUS_CHANGE");
        entry.setUserId(userId);
        entry.setDetails(details);
        auditLogRepository.save(entry);
    }

    private void saveEventLog(UUID entityId, String entityType, String action,
                              String userId, String details) {
        AuditLog entry = new AuditLog();
        entry.setEntityType(entityType);
        entry.setEntityId(entityId);
        entry.setAction(action);
        entry.setUserId(userId);
        entry.setDetails(details);
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

    private List<TransactionResponse.TimelineEntry> buildTimeline(UUID txId) {
        return auditLogRepository.findByTransactionIdOrderByTimestampAsc(txId)
                .stream()
                .filter(e -> e.getToStatus() != null)
                .map(e -> new TransactionResponse.TimelineEntry(
                        e.getFromStatus(),
                        e.getToStatus(),
                        e.getUserId(),
                        e.getTimestamp(),
                        e.getDetails()))
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
