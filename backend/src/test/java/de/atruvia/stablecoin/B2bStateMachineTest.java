package de.atruvia.stablecoin;

import de.atruvia.stablecoin.client.CoreBankingClient;
import de.atruvia.stablecoin.entity.StablecoinTransaction;
import de.atruvia.stablecoin.entity.TransactionStatus;
import de.atruvia.stablecoin.repository.AuditLogRepository;
import de.atruvia.stablecoin.repository.OutboxMessageRepository;
import de.atruvia.stablecoin.repository.StablecoinTransactionRepository;
import de.atruvia.stablecoin.service.b2b.B2bTransferService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static de.atruvia.stablecoin.entity.TransactionStatus.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit-Tests für die State Machine in B2bTransferService.
 * Kein Spring-Context, kein Docker — pure Mockito-Tests.
 */
class B2bStateMachineTest {

    @Mock StablecoinTransactionRepository txRepository;
    @Mock AuditLogRepository auditLogRepository;
    @Mock OutboxMessageRepository outboxRepository;
    @Mock CoreBankingClient coreBankingClient;

    private B2bTransferService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new B2bTransferService(
                txRepository, null, null, outboxRepository,
                auditLogRepository, null, null, null,
                coreBankingClient, null, null, null,
                null, null, null, null, null);
    }

    private StablecoinTransaction txWithStatus(TransactionStatus status) {
        StablecoinTransaction tx = new StablecoinTransaction();
        ReflectionTestUtils.setField(tx, "id", UUID.randomUUID());
        tx.setStatus(status);
        return tx;
    }

    private StablecoinTransaction txWithStatusAndHold(TransactionStatus status, String holdId) {
        StablecoinTransaction tx = txWithStatus(status);
        tx.setHoldId(holdId);
        return tx;
    }

    private void mockFindById(StablecoinTransaction tx) {
        UUID txId = (UUID) ReflectionTestUtils.getField(tx, "id");
        when(txRepository.findByIdWithLock(txId)).thenReturn(Optional.of(tx));
        when(txRepository.save(any())).thenReturn(tx);
        when(auditLogRepository.save(any())).thenReturn(null);
        when(outboxRepository.save(any())).thenReturn(null);
    }

    // ── TC-SM-01: Alle gültigen Übergänge ─────────────────────────────────────

    static Stream<Arguments> validTransitions() {
        return Stream.of(
            Arguments.of(CREATED,            PENDING_APPROVAL),
            Arguments.of(CREATED,            COMPLIANCE_CHECKED),
            Arguments.of(CREATED,            FAILED),
            Arguments.of(PENDING_APPROVAL,   APPROVED),
            Arguments.of(PENDING_APPROVAL,   REJECTED),
            Arguments.of(PENDING_APPROVAL,   EXPIRED),
            Arguments.of(PENDING_APPROVAL,   FAILED),
            Arguments.of(APPROVED,           COMPLIANCE_CHECKED),
            Arguments.of(APPROVED,           FAILED),
            Arguments.of(COMPLIANCE_CHECKED, FUNDS_HELD),
            Arguments.of(COMPLIANCE_CHECKED, FAILED),
            Arguments.of(FUNDS_HELD,         SUBMITTED),
            Arguments.of(SUBMITTED,          SETTLED),
            Arguments.of(SETTLED,            REDEEMED),
            Arguments.of(SETTLED,            FAILED)
        );
    }

    @ParameterizedTest(name = "{0} → {1}")
    @MethodSource("validTransitions")
    @DisplayName("TC-SM-01: Gültige Übergänge werden ohne Exception ausgeführt")
    void validTransition_noException(TransactionStatus from, TransactionStatus to) {
        StablecoinTransaction tx = txWithStatus(from);
        mockFindById(tx);
        UUID txId = (UUID) ReflectionTestUtils.getField(tx, "id");

        service.transitionTo(txId, to, "user-a");

        assertThat(tx.getStatus()).isEqualTo(to);
        verify(txRepository).save(tx);
    }

    // ── TC-SM-02: Ungültige Übergänge ─────────────────────────────────────────

    static Stream<Arguments> invalidTransitions() {
        return Stream.of(
            Arguments.of(CREATED,  SETTLED,   "CREATED → SETTLED überspringt Zwischenstufen"),
            Arguments.of(SETTLED,  CREATED,   "Rückwärtsübergang nicht erlaubt"),
            Arguments.of(SETTLED,  PENDING_APPROVAL, "SETTLED → PENDING_APPROVAL nicht erlaubt"),
            Arguments.of(SUBMITTED, COMPLIANCE_CHECKED, "Rückwärts SUBMITTED → COMPLIANCE_CHECKED")
        );
    }

    @ParameterizedTest(name = "{0} → {1}: {2}")
    @MethodSource("invalidTransitions")
    @DisplayName("TC-SM-02: Ungültige Übergänge werfen IllegalStateException")
    void invalidTransition_throwsIllegalState(TransactionStatus from, TransactionStatus to, String desc) {
        StablecoinTransaction tx = txWithStatus(from);
        mockFindById(tx);
        UUID txId = (UUID) ReflectionTestUtils.getField(tx, "id");

        assertThatThrownBy(() -> service.transitionTo(txId, to, "user-a"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Ungültiger Statusübergang");
    }

    // ── TC-SM-03: Terminale Zustände blockieren alle weiteren Übergänge ───────

    static Stream<TransactionStatus> terminalStates() {
        return Stream.of(REDEEMED, REJECTED, EXPIRED, FAILED);
    }

    @ParameterizedTest(name = "Terminal: {0}")
    @MethodSource("terminalStates")
    @DisplayName("TC-SM-03: Terminale Zustände erlauben keine weiteren Übergänge")
    void terminalState_blocksAllTransitions(TransactionStatus terminal) {
        StablecoinTransaction tx = txWithStatus(terminal);
        mockFindById(tx);
        UUID txId = (UUID) ReflectionTestUtils.getField(tx, "id");

        assertThatThrownBy(() -> service.transitionTo(txId, SETTLED, "user-a"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Ungültiger Statusübergang");
        assertThatThrownBy(() -> service.transitionTo(txId, CREATED, "user-a"))
                .isInstanceOf(IllegalStateException.class);
    }

    // ── TC-SM-04: FAILED aus FUNDS_HELD → releaseHold() wird aufgerufen ───────

    @Test
    @DisplayName("TC-SM-04: FAILED aus FUNDS_HELD löst Auto-Hold-Release aus")
    void failedFromFundsHeld_triggersReleaseHold() {
        String holdId = "hold-abc123";
        StablecoinTransaction tx = txWithStatusAndHold(FUNDS_HELD, holdId);
        mockFindById(tx);
        UUID txId = (UUID) ReflectionTestUtils.getField(tx, "id");

        service.transitionToFailed(txId, "Taurus timeout", "user-a");

        verify(coreBankingClient).releaseHold(holdId);
        assertThat(tx.getStatus()).isEqualTo(FAILED);
        assertThat(tx.getFailureReason()).isEqualTo("Taurus timeout");
    }

    // ── TC-SM-05: FAILED aus SUBMITTED → releaseHold() wird aufgerufen ────────

    @Test
    @DisplayName("TC-SM-05: FAILED aus SUBMITTED löst Auto-Hold-Release aus")
    void failedFromSubmitted_triggersReleaseHold() {
        String holdId = "hold-xyz789";
        StablecoinTransaction tx = txWithStatusAndHold(SUBMITTED, holdId);
        mockFindById(tx);
        UUID txId = (UUID) ReflectionTestUtils.getField(tx, "id");

        service.transitionToFailed(txId, "Circle API error", "user-a");

        verify(coreBankingClient).releaseHold(holdId);
        assertThat(tx.getStatus()).isEqualTo(FAILED);
    }

    // ── TC-SM-06: FAILED aus COMPLIANCE_CHECKED → KEIN releaseHold() ─────────

    @Test
    @DisplayName("TC-SM-06: FAILED aus COMPLIANCE_CHECKED löst KEINEN Hold-Release aus (kein Hold)")
    void failedFromComplianceChecked_noReleaseHold() {
        StablecoinTransaction tx = txWithStatus(COMPLIANCE_CHECKED);
        mockFindById(tx);
        UUID txId = (UUID) ReflectionTestUtils.getField(tx, "id");

        service.transitionToFailed(txId, "Compliance block", "user-a");

        verify(coreBankingClient, never()).releaseHold(anyString());
        assertThat(tx.getStatus()).isEqualTo(FAILED);
    }

    // ── TC-SM-07: SETTLED → REDEEMED (Yield-Redeem-Pfad) ─────────────────────

    @Test
    @DisplayName("TC-SM-07: SETTLED → REDEEMED ist erlaubt (Yield-Redeem-Pfad)")
    void settledToRedeemed_allowed() {
        StablecoinTransaction tx = txWithStatus(SETTLED);
        mockFindById(tx);
        UUID txId = (UUID) ReflectionTestUtils.getField(tx, "id");

        service.transitionTo(txId, REDEEMED, "user-b");

        assertThat(tx.getStatus()).isEqualTo(REDEEMED);
        verify(coreBankingClient, never()).releaseHold(anyString());
    }
}
