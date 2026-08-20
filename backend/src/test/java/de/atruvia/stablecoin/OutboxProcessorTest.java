package de.atruvia.stablecoin;

import de.atruvia.stablecoin.client.CircleWalletClient;
import de.atruvia.stablecoin.client.N8nWebhookClient;
import de.atruvia.stablecoin.client.dto.CircleTransactionStatusDto;
import de.atruvia.stablecoin.config.TenantContext;
import de.atruvia.stablecoin.entity.CustomerAccount;
import de.atruvia.stablecoin.entity.OutboxMessage;
import de.atruvia.stablecoin.entity.OutboxStatus;
import de.atruvia.stablecoin.entity.StablecoinCurrency;
import de.atruvia.stablecoin.entity.StablecoinTransaction;
import de.atruvia.stablecoin.entity.TransactionStatus;
import de.atruvia.stablecoin.outbox.OutboxProcessor;
import de.atruvia.stablecoin.repository.OutboxMessageRepository;
import de.atruvia.stablecoin.repository.StablecoinTransactionRepository;
import de.atruvia.stablecoin.service.b2b.B2bTransferService;
import de.atruvia.stablecoin.service.inbound.InboundProcessingService;
import de.atruvia.stablecoin.service.revenue.RevenueService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class OutboxProcessorTest {

    @Mock private OutboxMessageRepository outboxRepository;
    @Mock private N8nWebhookClient n8nWebhookClient;
    @Mock private StablecoinTransactionRepository txRepository;
    @Mock private CircleWalletClient circleWalletClient;
    @Mock private RevenueService revenueService;
    @Mock private B2bTransferService transferService;
    @Mock private InboundProcessingService inboundProcessingService;
    @Mock private JdbcTemplate adminJdbcTemplate;
    @Mock private de.atruvia.stablecoin.service.b2b.KillSwitchService killSwitchService;

    private OutboxProcessor processor;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        // Kill-Switch standardmäßig INAKTIV (Tests sollen normal verarbeiten)
        when(killSwitchService.isGlobalKillSwitchActive()).thenReturn(false);
        processor = new OutboxProcessor(
                outboxRepository, n8nWebhookClient, txRepository,
                circleWalletClient, revenueService, adminJdbcTemplate, killSwitchService);
        ReflectionTestUtils.setField(processor, "transferService", transferService);
        ReflectionTestUtils.setField(processor, "inboundProcessingService", inboundProcessingService);
        // T-01-Fix: adminJdbcTemplate.queryForList() liefert Tenant-ID für BYPASSRLS-Lookup
        // Varargs-kompatibles Mockito-Matching (Object... args)
        when(adminJdbcTemplate.queryForList(anyString(), eq(String.class), (Object[]) any()))
                .thenReturn(List.of("tenant-default"));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private OutboxMessage pendingMsg(String eventType, UUID txId) {
        OutboxMessage msg = new OutboxMessage();
        ReflectionTestUtils.setField(msg, "id", UUID.randomUUID());
        msg.setTransactionId(txId);
        msg.setEventType(eventType);
        msg.setStatus(OutboxStatus.PENDING);
        msg.setAttempts(0);
        return msg;
    }

    @Test
    @DisplayName("TRANSACTION_SETTLED event is log-only -> status becomes SENT")
    void transactionSettled_logOnly_markedSent() {
        UUID txId = UUID.randomUUID();
        OutboxMessage msg = pendingMsg("TRANSACTION_SETTLED", txId);
        when(outboxRepository.findByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING, PageRequest.of(0, 10)))
                .thenReturn(List.of(msg));

        processor.processPendingMessages();

        assertThat(msg.getStatus()).isEqualTo(OutboxStatus.SENT);
        verify(txRepository, never()).findById(any());
    }

    @Test
    @DisplayName("TRANSACTION_INITIATED event is log-only -> status becomes SENT")
    void transactionInitiated_logOnly_markedSent() {
        UUID txId = UUID.randomUUID();
        OutboxMessage msg = pendingMsg("TRANSACTION_INITIATED", txId);
        when(outboxRepository.findByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING, PageRequest.of(0, 10)))
                .thenReturn(List.of(msg));

        processor.processPendingMessages();

        assertThat(msg.getStatus()).isEqualTo(OutboxStatus.SENT);
        verify(txRepository, never()).findById(any());
    }

    @Test
    @DisplayName("TRANSACTION_FAILED event is log-only -> status becomes SENT")
    void transactionFailed_logOnly_markedSent() {
        UUID txId = UUID.randomUUID();
        OutboxMessage msg = pendingMsg("TRANSACTION_FAILED", txId);
        when(outboxRepository.findByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING, PageRequest.of(0, 10)))
                .thenReturn(List.of(msg));

        processor.processPendingMessages();

        assertThat(msg.getStatus()).isEqualTo(OutboxStatus.SENT);
        verify(txRepository, never()).findById(any());
    }

    @Test
    @DisplayName("SUBMIT_TO_BLOCKCHAIN: TX not in DB -> marked SENT immediately")
    void submitToBlockchain_txNotFound_markedSent() {
        UUID txId = UUID.randomUUID();
        OutboxMessage msg = pendingMsg("SUBMIT_TO_BLOCKCHAIN", txId);
        when(outboxRepository.findByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING, PageRequest.of(0, 10)))
                .thenReturn(List.of(msg));
        when(txRepository.findById(txId)).thenReturn(Optional.empty());

        processor.processPendingMessages();

        assertThat(msg.getStatus()).isEqualTo(OutboxStatus.SENT);
        verify(circleWalletClient, never()).getTransactionStatus(any());
    }

    @Test
    @DisplayName("SUBMIT_TO_BLOCKCHAIN: TX already SETTLED (terminal) -> marked SENT, no Circle call")
    void submitToBlockchain_txTerminal_markedSent() {
        UUID txId = UUID.randomUUID();
        OutboxMessage msg = pendingMsg("SUBMIT_TO_BLOCKCHAIN", txId);
        when(outboxRepository.findByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING, PageRequest.of(0, 10)))
                .thenReturn(List.of(msg));
        StablecoinTransaction tx = new StablecoinTransaction();
        tx.setStatus(TransactionStatus.SETTLED);
        when(txRepository.findById(txId)).thenReturn(Optional.of(tx));

        processor.processPendingMessages();

        assertThat(msg.getStatus()).isEqualTo(OutboxStatus.SENT);
        verify(circleWalletClient, never()).getTransactionStatus(any());
    }

    @Test
    @DisplayName("SUBMIT_TO_BLOCKCHAIN: SUBMITTED + circleId + Circle COMPLETE -> settleTransaction called")
    void submitToBlockchain_submittedCircleComplete_settles() {
        UUID txId = UUID.randomUUID();
        OutboxMessage msg = pendingMsg("SUBMIT_TO_BLOCKCHAIN", txId);
        when(outboxRepository.findByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING, PageRequest.of(0, 10)))
                .thenReturn(List.of(msg));

        StablecoinTransaction tx = new StablecoinTransaction();
        ReflectionTestUtils.setField(tx, "id", txId);
        tx.setStatus(TransactionStatus.SUBMITTED);
        tx.setCircleTransactionId("circle-abc");
        tx.setAmountFiat(new BigDecimal("1000.00"));
        when(txRepository.findById(txId)).thenReturn(Optional.of(tx));

        CircleTransactionStatusDto circleStatus =
                new CircleTransactionStatusDto("circle-abc", "COMPLETE", "0xhash123", null);
        when(circleWalletClient.getTransactionStatus("circle-abc")).thenReturn(circleStatus);

        RevenueService.RevenueCalculation revenue = new RevenueService.RevenueCalculation(
                BigDecimal.valueOf(0.0015), BigDecimal.valueOf(1.5),
                BigDecimal.valueOf(2.50), BigDecimal.valueOf(0.008), BigDecimal.valueOf(3.992));
        when(revenueService.calculate(any(), any())).thenReturn(revenue);

        processor.processPendingMessages();

        verify(transferService).settleTransaction(txId, "0xhash123", revenue);
        assertThat(msg.getStatus()).isEqualTo(OutboxStatus.SENT);
    }

    @Test
    @DisplayName("SUBMIT_TO_BLOCKCHAIN: SUBMITTED + circleId + Circle FAILED -> transitionToFailed called")
    void submitToBlockchain_submittedCircleFailed_transitions() {
        UUID txId = UUID.randomUUID();
        OutboxMessage msg = pendingMsg("SUBMIT_TO_BLOCKCHAIN", txId);
        when(outboxRepository.findByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING, PageRequest.of(0, 10)))
                .thenReturn(List.of(msg));

        StablecoinTransaction tx = new StablecoinTransaction();
        ReflectionTestUtils.setField(tx, "id", txId);
        tx.setStatus(TransactionStatus.SUBMITTED);
        tx.setCircleTransactionId("circle-def");
        when(txRepository.findById(txId)).thenReturn(Optional.of(tx));

        CircleTransactionStatusDto circleStatus =
                new CircleTransactionStatusDto("circle-def", "FAILED", null, null);
        when(circleWalletClient.getTransactionStatus("circle-def")).thenReturn(circleStatus);

        processor.processPendingMessages();

        verify(transferService).transitionToFailed(txId, "CIRCLE_FAILED_ON_CHAIN (Recovery)", "SYSTEM");
        assertThat(msg.getStatus()).isEqualTo(OutboxStatus.SENT);
    }

    @Test
    @DisplayName("SUBMIT_TO_BLOCKCHAIN: SUBMITTED + circleId + Circle PENDING -> attempts incremented")
    void submitToBlockchain_submittedCirclePending_incrementsAttempts() {
        UUID txId = UUID.randomUUID();
        OutboxMessage msg = pendingMsg("SUBMIT_TO_BLOCKCHAIN", txId);
        msg.setAttempts(2);
        when(outboxRepository.findByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING, PageRequest.of(0, 10)))
                .thenReturn(List.of(msg));

        StablecoinTransaction tx = new StablecoinTransaction();
        ReflectionTestUtils.setField(tx, "id", txId);
        tx.setStatus(TransactionStatus.SUBMITTED);
        tx.setCircleTransactionId("circle-xyz");
        when(txRepository.findById(txId)).thenReturn(Optional.of(tx));

        CircleTransactionStatusDto circleStatus =
                new CircleTransactionStatusDto("circle-xyz", "PENDING", null, null);
        when(circleWalletClient.getTransactionStatus("circle-xyz")).thenReturn(circleStatus);

        processor.processPendingMessages();

        assertThat(msg.getAttempts()).isEqualTo(3);
        assertThat(msg.getStatus()).isEqualTo(OutboxStatus.PENDING);
    }

    @Test
    @DisplayName("SUBMIT_TO_BLOCKCHAIN: SUBMITTED without circleId -> throws, attempts incremented")
    void submitToBlockchain_submittedNoCircleId_incrementsAttempts() {
        UUID txId = UUID.randomUUID();
        OutboxMessage msg = pendingMsg("SUBMIT_TO_BLOCKCHAIN", txId);
        when(outboxRepository.findByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING, PageRequest.of(0, 10)))
                .thenReturn(List.of(msg));

        StablecoinTransaction tx = new StablecoinTransaction();
        ReflectionTestUtils.setField(tx, "id", txId);
        tx.setStatus(TransactionStatus.SUBMITTED);
        tx.setCircleTransactionId(null);
        when(txRepository.findById(txId)).thenReturn(Optional.of(tx));

        processor.processPendingMessages();

        assertThat(msg.getAttempts()).isEqualTo(1);
        assertThat(msg.getStatus()).isEqualTo(OutboxStatus.PENDING);
    }

    @Test
    @DisplayName("SUBMIT_TO_BLOCKCHAIN: FUNDS_HELD -> throws, attempts incremented")
    void submitToBlockchain_fundsHeld_incrementsAttempts() {
        UUID txId = UUID.randomUUID();
        OutboxMessage msg = pendingMsg("SUBMIT_TO_BLOCKCHAIN", txId);
        msg.setAttempts(1);
        when(outboxRepository.findByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING, PageRequest.of(0, 10)))
                .thenReturn(List.of(msg));

        StablecoinTransaction tx = new StablecoinTransaction();
        ReflectionTestUtils.setField(tx, "id", txId);
        tx.setStatus(TransactionStatus.FUNDS_HELD);
        tx.setHoldId("hold-99");
        when(txRepository.findById(txId)).thenReturn(Optional.of(tx));

        processor.processPendingMessages();

        assertThat(msg.getAttempts()).isEqualTo(2);
        assertThat(msg.getStatus()).isEqualTo(OutboxStatus.PENDING);
    }

    @Test
    @DisplayName("Reaching MAX_ATTEMPTS (5) -> message permanently marked FAILED")
    void maxAttempts_permanentlyFailed() {
        UUID txId = UUID.randomUUID();
        OutboxMessage msg = pendingMsg("SUBMIT_TO_BLOCKCHAIN", txId);
        msg.setAttempts(4); // will reach 5 = MAX_ATTEMPTS after increment
        when(outboxRepository.findByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING, PageRequest.of(0, 10)))
                .thenReturn(List.of(msg));

        StablecoinTransaction tx = new StablecoinTransaction();
        ReflectionTestUtils.setField(tx, "id", txId);
        tx.setStatus(TransactionStatus.FUNDS_HELD);
        when(txRepository.findById(txId)).thenReturn(Optional.of(tx));

        processor.processPendingMessages();

        assertThat(msg.getAttempts()).isEqualTo(5);
        assertThat(msg.getStatus()).isEqualTo(OutboxStatus.FAILED);
    }

    // ── Multi-Tenancy Recovery Tests ──────────────────────────────────────────

    @Test
    @DisplayName("PROCESS_INBOUND_COMPLIANCE: TX nicht in DB (adminJdbc null) -> SENT, kein Compliance-Flow")
    void processInboundCompliance_txNotFound_markedSent() {
        UUID txId = UUID.randomUUID();
        OutboxMessage msg = pendingMsg("PROCESS_INBOUND_COMPLIANCE", txId);
        when(outboxRepository.findByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING, PageRequest.of(0, 10)))
                .thenReturn(List.of(msg));

        // adminJdbcTemplate liefert leere Liste (TX existiert nicht)
        when(adminJdbcTemplate.queryForList(anyString(), eq(String.class), any(UUID.class)))
                .thenReturn(List.of());

        processor.processPendingMessages();

        assertThat(msg.getStatus()).isEqualTo(OutboxStatus.SENT);
        verify(inboundProcessingService, never()).executeInboundComplianceFlow(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("PROCESS_INBOUND_COMPLIANCE: INCOMING → setzt TenantContext + startet Compliance-Flow")
    void processInboundCompliance_incomingTx_setsTenantContextAndStartsFlow() {
        UUID txId = UUID.randomUUID();
        String tenantId = "tenant-test-vb";
        OutboxMessage msg = pendingMsg("PROCESS_INBOUND_COMPLIANCE", txId);
        when(outboxRepository.findByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING, PageRequest.of(0, 10)))
                .thenReturn(List.of(msg));

        // adminJdbcTemplate liefert tenantId (BYPASSRLS-Lookup)
        when(adminJdbcTemplate.queryForList(anyString(), eq(String.class), any(UUID.class)))
                .thenReturn(List.of(tenantId));

        // TX mit INCOMING-Status aufbauen
        CustomerAccount account = new CustomerAccount();
        account.setIban("DE89370400440532013000");

        StablecoinTransaction tx = new StablecoinTransaction();
        ReflectionTestUtils.setField(tx, "id", txId);
        tx.setStatus(TransactionStatus.INCOMING);
        tx.setSourceWallet("0xSender000");
        tx.setCurrency(StablecoinCurrency.USDC);
        tx.setAmountFiat(BigDecimal.valueOf(500));
        ReflectionTestUtils.setField(tx, "customerAccount", account);

        when(txRepository.findById(txId)).thenReturn(Optional.of(tx));

        processor.processPendingMessages();

        // Compliance-Flow wurde gestartet
        verify(inboundProcessingService).executeInboundComplianceFlow(
                eq(txId),
                eq("0xSender000"),
                eq(StablecoinCurrency.USDC),
                eq(BigDecimal.valueOf(500)),
                eq("DE89370400440532013000"));
        assertThat(msg.getStatus()).isEqualTo(OutboxStatus.SENT);

        // TenantContext ist nach finally-Block gesäubert
        assertThat(TenantContext.get()).isNull();
    }

    @Test
    @DisplayName("PROCESS_INBOUND_COMPLIANCE: SETTLED (Terminal) → kein Compliance-Flow (idempotent)")
    void processInboundCompliance_terminalTx_noAction() {
        UUID txId = UUID.randomUUID();
        String tenantId = "tenant-test-vb";
        OutboxMessage msg = pendingMsg("PROCESS_INBOUND_COMPLIANCE", txId);
        when(outboxRepository.findByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING, PageRequest.of(0, 10)))
                .thenReturn(List.of(msg));

        when(adminJdbcTemplate.queryForList(anyString(), eq(String.class), any(UUID.class)))
                .thenReturn(List.of(tenantId));

        StablecoinTransaction tx = new StablecoinTransaction();
        ReflectionTestUtils.setField(tx, "id", txId);
        tx.setStatus(TransactionStatus.SETTLED);
        when(txRepository.findById(txId)).thenReturn(Optional.of(tx));

        processor.processPendingMessages();

        assertThat(msg.getStatus()).isEqualTo(OutboxStatus.SENT);
        verify(inboundProcessingService, never()).executeInboundComplianceFlow(any(), any(), any(), any(), any());
        // TenantContext gesäubert
        assertThat(TenantContext.get()).isNull();
    }
}
