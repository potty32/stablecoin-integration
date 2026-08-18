package de.atruvia.stablecoin;

import de.atruvia.stablecoin.client.AtruviaTaxClient;
import de.atruvia.stablecoin.client.CoreBankingClient;
import de.atruvia.stablecoin.client.dto.BookingResponseDto;
import de.atruvia.stablecoin.client.dto.TaxReportResponseDto;
import de.atruvia.stablecoin.dto.request.b2c.YieldDepositRequest;
import de.atruvia.stablecoin.dto.response.YieldPositionResponse;
import de.atruvia.stablecoin.entity.*;
import de.atruvia.stablecoin.repository.*;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class B2cYieldServiceTest {

    @Mock StablecoinTransactionRepository txRepository;
    @Mock CustomerAccountRepository accountRepository;
    @Mock AuditLogRepository auditLogRepository;
    @Mock YieldPositionRepository yieldPositionRepository;
    @Mock TaxEventRepository taxEventRepository;
    @Mock AtruviaTaxClient atruviaTaxClient;
    @Mock CoreBankingClient coreBankingClient;

    private B2cYieldService service;
    private CustomerAccount b2cAccount;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new B2cYieldService(txRepository, accountRepository, auditLogRepository,
                yieldPositionRepository, taxEventRepository, atruviaTaxClient, coreBankingClient);

        b2cAccount = new CustomerAccount();
        ReflectionTestUtils.setField(b2cAccount, "id", UUID.randomUUID());
        b2cAccount.setCustomerId("cust-b2c-001");
        b2cAccount.setIban("DE27200400600532013001");
        b2cAccount.setWalletAddress("0xCustomerWallet001");

        when(txRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(auditLogRepository.save(any())).thenReturn(null);
        when(taxEventRepository.save(any())).thenReturn(null);
        when(coreBankingClient.createLedgerBooking(any()))
                .thenReturn(new BookingResponseDto("booking-test", "BOOKED", LocalDateTime.now()));
    }

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

        ArgumentCaptor<StablecoinTransaction> txCaptor = ArgumentCaptor.forClass(StablecoinTransaction.class);
        verify(txRepository).save(txCaptor.capture());
        assertThat(txCaptor.getValue().getType()).isEqualTo(TransactionType.YIELD_DEPOSIT);
        assertThat(txCaptor.getValue().getStatus()).isEqualTo(TransactionStatus.SETTLED);
        assertThat(txCaptor.getValue().getAmountFiat()).isEqualByComparingTo(new BigDecimal("2000.00"));

        ArgumentCaptor<YieldPosition> posCaptor = ArgumentCaptor.forClass(YieldPosition.class);
        verify(yieldPositionRepository).save(posCaptor.capture());
        assertThat(posCaptor.getValue().getStatus()).isEqualTo(YieldStatus.ACTIVE);
        assertThat(response.positionId()).isEqualTo(savedPosition.getId());

        // G-02: Tax-Client wird bei deposit() NICHT aufgerufen
        verifyNoInteractions(atruviaTaxClient);
    }

    @Test
    @DisplayName("TC-Y-02: redeem() delegiert Steuer an AtruviaTaxClient und bucht Nettobetrag")
    void redeem_delegatesTaxAndBooksNetto() {
        UUID positionId = UUID.randomUUID();
        YieldPosition activePosition = new YieldPosition();
        ReflectionTestUtils.setField(activePosition, "id", positionId);
        activePosition.setCustomerAccount(b2cAccount);
        activePosition.setPrincipal(new BigDecimal("1000.00"));
        activePosition.setInterestRate(new BigDecimal("0.035"));
        activePosition.setDepositedAt(LocalDateTime.now().minusDays(30));
        activePosition.setStatus(YieldStatus.ACTIVE);

        when(yieldPositionRepository.findById(positionId)).thenReturn(Optional.of(activePosition));
        when(yieldPositionRepository.save(any())).thenReturn(activePosition);

        // AtruviaTaxClient: FSA_COVERED (Ertrag < 1000 EUR), kein Steuerabzug
        BigDecimal netPayout = new BigDecimal("2.87");
        TaxReportResponseDto taxResponse = new TaxReportResponseDto(
                "TAX-MOCK-001", BigDecimal.ZERO, netPayout, "FSA_COVERED");
        when(atruviaTaxClient.reportCapitalGain(any())).thenReturn(taxResponse);

        StablecoinTransaction savedRedeemTx = new StablecoinTransaction();
        ReflectionTestUtils.setField(savedRedeemTx, "id", UUID.randomUUID());
        savedRedeemTx.setStatus(TransactionStatus.REDEEMED);
        when(txRepository.save(any())).thenReturn(savedRedeemTx);

        service.redeem(positionId, "cust-b2c-001");

        // AtruviaTaxClient aufgerufen mit korrektem customerId und positivem Ertrag
        verify(atruviaTaxClient).reportCapitalGain(argThat(req ->
                "cust-b2c-001".equals(req.customerId()) && req.grossYieldEur().compareTo(BigDecimal.ZERO) > 0));

        // CoreBanking-Buchung mit Nettobetrag
        verify(coreBankingClient).createLedgerBooking(argThat(b ->
                b.totalAmount().compareTo(netPayout) == 0));

        // TaxEvent gespeichert
        verify(taxEventRepository).save(any());

        // YIELD_REDEEM TX: amountFiat = Brutto (>principal), taxWithheld = 0
        ArgumentCaptor<StablecoinTransaction> txCap = ArgumentCaptor.forClass(StablecoinTransaction.class);
        verify(txRepository).save(txCap.capture());
        assertThat(txCap.getValue().getType()).isEqualTo(TransactionType.YIELD_REDEEM);
        assertThat(txCap.getValue().getAmountFiat()).isGreaterThan(new BigDecimal("1000.00"));
        assertThat(txCap.getValue().getTaxWithheld()).isEqualByComparingTo(BigDecimal.ZERO);

        // YieldPosition geschlossen
        assertThat(activePosition.getStatus()).isEqualTo(YieldStatus.CLOSED);
    }

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
        when(yieldPositionRepository.findByCustomerAccountIdAndStatus(any(UUID.class), eq(YieldStatus.ACTIVE)))
                .thenReturn(Optional.of(position));

        YieldPositionResponse response = service.getPosition("cust-b2c-001");
        assertThat(response.positionId()).isEqualTo(positionId);
        assertThat(response.currentValueEur()).isGreaterThan(new BigDecimal("500.00"));
    }

    @Test
    @DisplayName("TC-Y-03b: getPosition() wirft NoSuchElementException wenn keine ACTIVE Position")
    void getPosition_throwsWhenNoActivePosition() {
        when(accountRepository.findByCustomerId("cust-b2c-001")).thenReturn(Optional.of(b2cAccount));
        when(yieldPositionRepository.findByCustomerAccountIdAndStatus(any(), eq(YieldStatus.ACTIVE)))
                .thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getPosition("cust-b2c-001"))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    @DisplayName("TC-Y-04: Compound-Zinsformel: 2000 EUR × 7 Tage → ~2001.34 EUR")
    void compoundInterest_correctAfter7Days() {
        BigDecimal principal = new BigDecimal("2000.00");
        BigDecimal actual = service.computeCurrentValue(principal, 7);
        // actual > principal: Zinsertrag vorhanden
        assertThat(actual.subtract(principal)).isGreaterThan(BigDecimal.ZERO);
        assertThat(actual).isGreaterThan(principal);
        assertThat(actual).isBetween(new BigDecimal("2001.30"), new BigDecimal("2001.40"));
    }
}
