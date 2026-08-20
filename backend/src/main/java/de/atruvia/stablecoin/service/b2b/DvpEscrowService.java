package de.atruvia.stablecoin.service.b2b;

import de.atruvia.stablecoin.client.TokenAdapterRouter;
import de.atruvia.stablecoin.client.CoreBankingClient;
import de.atruvia.stablecoin.client.dto.AdapterTransferRequest;
import de.atruvia.stablecoin.client.dto.AdapterTransferResult;
import de.atruvia.stablecoin.client.dto.CreateHoldDto;
import de.atruvia.stablecoin.client.dto.HoldResponseDto;
import de.atruvia.stablecoin.client.dto.LedgerBookingDto;
import de.atruvia.stablecoin.config.TenantContext;
import de.atruvia.stablecoin.dto.request.b2b.DvpCancelRequest;
import de.atruvia.stablecoin.dto.request.b2b.DvpLockRequest;
import de.atruvia.stablecoin.dto.request.b2b.DvpSettleRequest;
import de.atruvia.stablecoin.dto.response.DvpEscrowResponse;
import de.atruvia.stablecoin.entity.CustomerAccount;
import de.atruvia.stablecoin.entity.DvpEscrow;
import de.atruvia.stablecoin.entity.DvpEscrowStatus;
import de.atruvia.stablecoin.entity.TenantSettings;
import de.atruvia.stablecoin.repository.CustomerAccountRepository;
import de.atruvia.stablecoin.repository.DvpEscrowRepository;
import de.atruvia.stablecoin.service.fx.FxRateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Orchestriert den atomaren Delivery-versus-Payment-Prozess.
 *
 * Eliminiert Herstatt-Risiko durch atomare Sperre (lock) → Freigabe (settle)
 * oder Rückgabe (cancel) des Stablecoin-Betrages.
 *
 * RLS: Alle Escrow-Abfragen laufen über stablecoin_app (RLS-aktiv).
 * Ein Mandant kann nur seine eigenen Escrows sehen und bearbeiten.
 */
@Service
@Transactional
public class DvpEscrowService {

    private static final Logger log = LoggerFactory.getLogger(DvpEscrowService.class);
    private static final String DVP_ESCROW_ACCOUNT = "DVP_ESCROW_INTERNAL_ACCOUNT";
    private static final String REVENUE_IBAN        = "DE00ATRUVIA0001ERTRAG";

    private final DvpEscrowRepository dvpEscrowRepository;
    private final CustomerAccountRepository accountRepository;
    private final TenantSettingsService tenantSettingsService;
    private final FxRateService fxRateService;
    private final TokenAdapterRouter tokenAdapterRouter;
    private final CoreBankingClient coreBankingClient;

    public DvpEscrowService(DvpEscrowRepository dvpEscrowRepository,
                             CustomerAccountRepository accountRepository,
                             TenantSettingsService tenantSettingsService,
                             FxRateService fxRateService,
                             TokenAdapterRouter tokenAdapterRouter,
                             CoreBankingClient coreBankingClient) {
        this.dvpEscrowRepository  = dvpEscrowRepository;
        this.accountRepository    = accountRepository;
        this.tenantSettingsService = tenantSettingsService;
        this.fxRateService        = fxRateService;
        this.tokenAdapterRouter   = tokenAdapterRouter;
        this.coreBankingClient    = coreBankingClient;
    }

    /**
     * UC-33: Sperrt einen Stablecoin-Betrag im DvP-Escrow-Konto.
     * Debitiert den EUR-Betrag via CoreBanking-Hold auf dem Kundenkonto.
     */
    public DvpEscrowResponse lock(DvpLockRequest request, String userId) {
        CustomerAccount account = accountRepository.findByIban(request.sourceIban())
                .orElseThrow(() -> new NoSuchElementException("Konto nicht gefunden: " + request.sourceIban()));

        BigDecimal fxRate          = fxRateService.getBaseRate(request.currency());
        BigDecimal amountStablecoin = request.amountEur()
                .divide(fxRate, 6, RoundingMode.HALF_UP);

        HoldResponseDto hold = coreBankingClient.createHold(
                account.getIban(),
                new CreateHoldDto(request.amountEur(), "EUR",
                        "DvP Escrow: " + request.securitiesIsin(), request.escrowReference()));

        DvpEscrow escrow = new DvpEscrow();
        escrow.setCustomerAccount(account);
        escrow.setAmountFiat(request.amountEur());
        escrow.setAmountStablecoin(amountStablecoin);
        escrow.setCurrency(request.currency());
        escrow.setStatus(DvpEscrowStatus.ESCROWED);
        escrow.setSettlementWallet(request.settlementWallet());
        escrow.setSecuritiesIsin(request.securitiesIsin());
        escrow.setSecuritiesAmount(request.securitiesAmount());
        escrow.setEscrowReference(request.escrowReference());
        escrow.setSecuritiesSystemId(request.securitiesSystemId());
        escrow.setHoldId(hold.holdId());

        DvpEscrow saved = dvpEscrowRepository.save(escrow);
        log.info("[DvP] Escrow gesperrt: id={} ref={} amount={} {} für ISIN={} wallet={}",
                saved.getId(), saved.getEscrowReference(), saved.getAmountFiat(),
                saved.getCurrency(), saved.getSecuritiesIsin(), saved.getSettlementWallet());
        return toResponse(saved);
    }

    /**
     * UC-34: Gibt gesperrte Stablecoins frei, sobald die Wertpapierseite bestätigt.
     * Transferiert Stablecoins an das Settlement-Wallet des Händlers.
     * Bucht Gebühren auf das Ertrags-IBAN.
     */
    public DvpEscrowResponse settle(DvpSettleRequest request, String userId) {
        DvpEscrow escrow = loadEscrow(request.escrowId(), request.escrowReference());
        assertStatus(escrow, DvpEscrowStatus.ESCROWED, "settle");

        // Stablecoins via Token-Adapter an Händler-Wallet transferieren
        AdapterTransferResult adapterResult = tokenAdapterRouter.getAdapter(escrow.getCurrency())
                .initiateAndConfirm(new AdapterTransferRequest(
                        "dvp-settle-" + escrow.getId(),
                        "BANK_MASTER_WALLET_ID",
                        escrow.getSettlementWallet(),
                        escrow.getAmountStablecoin(),
                        escrow.getCurrency()));

        // Gebühr aus TenantSettings (B2B-Tarif)
        TenantSettings settings = tenantSettingsService.get(TenantContext.get());
        BigDecimal fee = settings.getFeeFlatB2bEur();
        BigDecimal netAmount = escrow.getAmountFiat().subtract(fee);

        // Ledger-Buchung: Escrow-Konto → Kundenkonto (Netto) + Ertrags-IBAN (Fee)
        coreBankingClient.createLedgerBooking(new LedgerBookingDto(
                "dvp-settle-" + escrow.getId(),
                DVP_ESCROW_ACCOUNT,
                List.of(
                        new LedgerBookingDto.CreditEntry(
                                escrow.getCustomerAccount().getIban(), netAmount, "DVP_SETTLE_NET"),
                        new LedgerBookingDto.CreditEntry(
                                REVENUE_IBAN, fee, "DVP_SETTLE_FEE")),
                escrow.getAmountFiat(), "EUR", LocalDate.now()));

        escrow.setStatus(DvpEscrowStatus.SETTLED);
        escrow.setSettledAt(LocalDateTime.now());
        escrow.setFeeAmount(fee);
        escrow.setBlockchainHash(adapterResult.blockchainHash());

        DvpEscrow saved = dvpEscrowRepository.save(escrow);
        log.info("[DvP] Escrow freigegeben: id={} hash={} fee={} EUR",
                saved.getId(), saved.getBlockchainHash(), saved.getFeeAmount());
        return toResponse(saved);
    }

    /**
     * UC-35: Löst den Escrow auf und gibt den gesperrten Betrag zurück.
     * Aktiviert wenn die Wertpapierübertragung gescheitert ist. Gebührenfrei.
     */
    public DvpEscrowResponse cancel(DvpCancelRequest request, String userId) {
        DvpEscrow escrow = loadEscrow(request.escrowId(), request.escrowReference());
        assertStatus(escrow, DvpEscrowStatus.ESCROWED, "cancel");

        // Hold aufheben → EUR zurück auf Kundenkonto
        coreBankingClient.releaseHold(escrow.getHoldId());

        escrow.setStatus(DvpEscrowStatus.CANCELLED);
        escrow.setCancelledAt(LocalDateTime.now());
        escrow.setCancellationReason(request.cancellationReason());

        DvpEscrow saved = dvpEscrowRepository.save(escrow);
        log.info("[DvP] Escrow storniert: id={} reason={}", saved.getId(), saved.getCancellationReason());
        return toResponse(saved);
    }

    // ── Private Helpers ──────────────────────────────────────────────────────

    private DvpEscrow loadEscrow(UUID id, String escrowReference) {
        return dvpEscrowRepository.findByIdAndEscrowReference(id, escrowReference)
                .orElseThrow(() -> new NoSuchElementException(
                        "DvP-Escrow nicht gefunden: id=" + id + " ref=" + escrowReference));
    }

    private void assertStatus(DvpEscrow escrow, DvpEscrowStatus expected, String operation) {
        if (escrow.getStatus() != expected) {
            throw new IllegalStateException(String.format(
                    "DvP-%s: Escrow %s ist nicht im Status %s sondern %s",
                    operation, escrow.getId(), expected, escrow.getStatus()));
        }
    }

    private DvpEscrowResponse toResponse(DvpEscrow e) {
        return new DvpEscrowResponse(
                e.getId(), e.getStatus(),
                e.getAmountFiat(), e.getAmountStablecoin(), e.getCurrency(),
                e.getSecuritiesIsin(), e.getSecuritiesAmount(), e.getEscrowReference(),
                e.getSecuritiesSystemId(), e.getFeeAmount(), e.getBlockchainHash(),
                e.getLockedAt(), e.getSettledAt(), e.getCancelledAt(), e.getCancellationReason());
    }
}
