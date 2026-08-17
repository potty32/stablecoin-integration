package de.atruvia.stablecoin;

import de.atruvia.stablecoin.dto.request.b2c.YieldDepositRequest;
import de.atruvia.stablecoin.dto.response.YieldPositionResponse;
import de.atruvia.stablecoin.entity.*;
import de.atruvia.stablecoin.repository.AuditLogRepository;
import de.atruvia.stablecoin.repository.CustomerAccountRepository;
import de.atruvia.stablecoin.repository.StablecoinTransactionRepository;
import de.atruvia.stablecoin.repository.YieldPositionRepository;
import de.atruvia.stablecoin.service.b2c.B2cYieldService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDateTime;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit-Tests für B2cYieldService.
 * Kein Spring-Context, kein Docker — pure Mockito-Tests.
 */
class B2cYieldServiceTest {

    @Mock StablecoinTransactionRepository txRepository;
    @Mock CustomerAccountRepository accountRepository;
    @Mock AuditLogRepository auditLogRepository;
    @Mock YieldPositionRepository yieldPositionRepository;

    private B2cYieldService service;

    private CustomerAccount b2cAccount;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new B2cYieldService(txRepository, accountRepository, auditLogRepository, yieldPositionRepository);

        b2cAccount = new CustomerAccount();
        ReflectionTestUtils.setField(b2cAccount, "id", UUID.randomUUID());
        b2cAccount.setCustomerId("cust-b2c-001");
        b2cAccount.setIban("DE27200400600532013001");
        b2cAccount.setWalletAddress("0xCustomerWallet001");

        // Default: idempotency-key not found (no conflict)
        when(txRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(auditLogRepository.save(any())).thenReturn(null);
    }

    // ── TC-Y-01: deposit() erstellt YIELD_DEPOSIT TX + ACTIVE YieldPosition ──

    @Test
    @DisplayName("TC-Y-01: deposit() erstellt YIELD_DEPOSIT TX (SETTLED) + ACTIVE YieldPosition")
    void deposit_createsDepositTxAndActivePosition() {
        when(accountRepository.findByIban("DE27200400600532013001")).thenReturn(Optional.of(b2cAccount));

        StablecoinTransaction savedTx = new StablecoinTransaction();
        ReflectionTestUtils.setField(savedTx, "id", UUID.randomUUID());
        savedTx.setStatus(TransactionStatus.SETTLED);
        savedTx.setType(TransactionType.YIELD_DEPOSIT);
        when(txRepository.save(any(StablecoinTransaction.class))).thenReturn(savedTx);

        YieldPosition savedPosition = new YieldPosition();
        ReflectionTestUtils.setField(savedPosition, "id", UUID.randomUUID());
        savedPosition.setCustomerAccount(b2cAccount);
        savedPosition.setPrincipal(new BigDecimal("2000.00"));
        savedPosition.setInterestRate(new BigDecimal("0.035"));
        savedPosition.setDepositedAt(LocalDateTime.now());
        savedPosition.setStatus(YieldStatus.ACTIVE);
        savedPosition.setDepositTransactionId(savedTx.getId());
        when(yieldPositionRepository.save(any(YieldPosition.class))).thenReturn(savedPosition);

        YieldDepositRequest request = new YieldDepositRequest("DE27200400600532013001", new BigDecimal("2000.00"));
        YieldPositionResponse response = service.deposit("idem-key-001", request, "cust-b2c-001");

        // TX gespeichert: YIELD_DEPOSIT, SETTLED
        ArgumentCaptor<StablecoinTransaction> txCaptor = ArgumentCaptor.forClass(StablecoinTransaction.class);
        verify(txRepository).save(txCaptor.capture());
        assertThat(txCaptor.getValue().getType()).isEqualTo(TransactionType.YIELD_DEPOSIT);
        assertThat(txCaptor.getValue().getStatus()).isEqualTo(TransactionStatus.SETTLED);
        assertThat(txCaptor.getValue().getAmountFiat()).isEqualByComparingTo(new BigDecimal("2000.00"));

        // YieldPosition gespeichert: ACTIVE
        ArgumentCaptor<YieldPosition> posCaptor = ArgumentCaptor.forClass(YieldPosition.class);
        verify(yieldPositionRepository).save(posCaptor.capture());
        assertThat(posCaptor.getValue().getStatus()).isEqualTo(YieldStatus.ACTIVE);
        assertThat(posCaptor.getValue().getPrincipal()).isEqualByComparingTo(new BigDecimal("2000.00"));

        // Response: positionId = YieldPosition.id (nicht TX.id)
        assertThat(response.positionId()).isEqualTo(savedPosition.getId());
        assertThat(response.status()).isEqualTo("ACTIVE");
    }

    // ── TC-Y-02: redeem() erstellt YIELD_REDEEM TX + schließt YieldPosition ──

    @Test
    @DisplayName("TC-Y-02: redeem() erstellt YIELD_REDEEM TX (REDEEMED) + setzt YieldPosition auf CLOSED")
    void redeem_createsRedeemTxAndClosesPosition() {
        UUID positionId = UUID.randomUUID();
        YieldPosition activePosition = new YieldPosition();
        ReflectionTestUtils.setField(activePosition, "id", positionId);
        activePosition.setCustomerAccount(b2cAccount);
        activePosition.setPrincipal(new BigDecimal("1000.00"));
        activePosition.setInterestRate(new BigDecimal("0.035"));
        activePosition.setDepositedAt(LocalDateTime.now().minusDays(30));
        activePosition.setStatus(YieldStatus.ACTIVE);

        when(yieldPositionRepository.findById(positionId)).thenReturn(Optional.of(activePosition));

        StablecoinTransaction savedRedeemTx = new StablecoinTransaction();
        ReflectionTestUtils.setField(savedRedeemTx, "id", UUID.randomUUID());
        savedRedeemTx.setStatus(TransactionStatus.REDEEMED);
        savedRedeemTx.setType(TransactionType.YIELD_REDEEM);
        when(txRepository.save(any(StablecoinTransaction.class))).thenReturn(savedRedeemTx);
        when(yieldPositionRepository.save(any(YieldPosition.class))).thenReturn(activePosition);

        service.redeem(positionId, "cust-b2c-001");

        // YIELD_REDEEM TX gespeichert
        ArgumentCaptor<StablecoinTransaction> txCaptor = ArgumentCaptor.forClass(StablecoinTransaction.class);
        verify(txRepository).save(txCaptor.capture());
        assertThat(txCaptor.getValue().getType()).isEqualTo(TransactionType.YIELD_REDEEM);
        assertThat(txCaptor.getValue().getStatus()).isEqualTo(TransactionStatus.REDEEMED);
        assertThat(txCaptor.getValue().getAmountFiat()).isGreaterThan(new BigDecimal("1000.00")); // Zinsen addiert

        // YieldPosition: CLOSED, closedAt gesetzt
        assertThat(activePosition.getStatus()).isEqualTo(YieldStatus.CLOSED);
        assertThat(activePosition.getClosedAt()).isNotNull();
        verify(yieldPositionRepository).save(activePosition);

        // Keine zweite TX — nur ein save
        verify(txRepository, times(1)).save(any());
    }

    // ── TC-Y-03: getPosition() → ACTIVE gefunden; 404 bei keiner Position ────

    @Test
    @DisplayName("TC-Y-03a: getPosition() gibt ACTIVE Position zurück")
    void getPosition_returnsActivePosition() {
        UUID positionId = UUID.randomUUID();
        YieldPosition position = new YieldPosition();
        ReflectionTestUtils.setField(position, "id", positionId);
        position.setCustomerAccount(b2cAccount);
        position.setPrincipal(new BigDecimal("500.00"));
        position.setInterestRate(new BigDecimal("0.035"));
        position.setDepositedAt(LocalDateTime.now().minusDays(7));
        position.setStatus(YieldStatus.ACTIVE);

        when(accountRepository.findByCustomerId("cust-b2c-001")).thenReturn(Optional.of(b2cAccount));
        when(yieldPositionRepository.findByCustomerAccountIdAndStatus(
                any(UUID.class), eq(YieldStatus.ACTIVE)))
                .thenReturn(Optional.of(position));

        YieldPositionResponse response = service.getPosition("cust-b2c-001");

        assertThat(response.positionId()).isEqualTo(positionId);
        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(response.currentValueEur()).isGreaterThan(new BigDecimal("500.00"));
    }

    @Test
    @DisplayName("TC-Y-03b: getPosition() wirft NoSuchElementException wenn keine ACTIVE Position")
    void getPosition_throwsWhenNoActivePosition() {
        when(accountRepository.findByCustomerId("cust-b2c-001")).thenReturn(Optional.of(b2cAccount));
        when(yieldPositionRepository.findByCustomerAccountIdAndStatus(any(), eq(YieldStatus.ACTIVE)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPosition("cust-b2c-001"))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("No active yield position");
    }

    // ── TC-Y-04: Compound-Zinsformel ──────────────────────────────────────────

    @Test
    @DisplayName("TC-Y-04: Compound-Zinsformel: 2000 EUR × 7 Tage → ~2001.34 EUR")
    void compoundInterest_correctAfter7Days() {
        BigDecimal principal = new BigDecimal("2000.00");
        BigDecimal expected = principal
                .multiply(BigDecimal.ONE.add(
                        new BigDecimal("0.035").divide(new BigDecimal("365"), MathContext.DECIMAL64),
                        MathContext.DECIMAL64)
                        .pow(7, MathContext.DECIMAL64), MathContext.DECIMAL64);

        BigDecimal actual = service.computeCurrentValue(principal, 7);

        // Differenz < 0.01 EUR (Rundungstoleranz)
        assertThat(actual.subtract(expected).abs()).isLessThan(new BigDecimal("0.01"));
        // Wert > Principal (Zinsen wurden addiert)
        assertThat(actual).isGreaterThan(principal);
        // Wert ≈ 2001.34 EUR
        assertThat(actual).isBetween(new BigDecimal("2001.30"), new BigDecimal("2001.40"));
    }
}
