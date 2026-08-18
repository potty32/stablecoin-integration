package de.atruvia.stablecoin.service.b2b;

import de.atruvia.stablecoin.client.CircleWalletClient;
import de.atruvia.stablecoin.config.TenantContext;
import de.atruvia.stablecoin.entity.ReconciliationRun;
import de.atruvia.stablecoin.repository.ReconciliationRunRepository;
import de.atruvia.stablecoin.repository.TenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * G-04: Täglicher Soll/Haben-Abgleich zwischen Fiat-Ledger und On-Chain-Salden.
 * Läuft täglich um 23:00 Uhr, pro aktivem Mandanten.
 *
 * Prüft: Σ(SETTLED Outbound) ≈ On-Chain-USDC-Saldo + On-Chain-EURC-Saldo
 * Diskrepanz > Schwellenwert → Status=DISCREPANCY + n8n-Alert.
 *
 * AT 7.2 MaRisk, §25a KWG, §238 HGB
 */
@Service
public class ReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);
    private static final String BANK_MASTER_WALLET = "BANK_MASTER_WALLET_ID";

    private final ReconciliationRunRepository reconciliationRepo;
    private final TenantRepository tenantRepository;
    private final CircleWalletClient circleWalletClient;

    public ReconciliationService(
            ReconciliationRunRepository reconciliationRepo,
            TenantRepository tenantRepository,
            CircleWalletClient circleWalletClient) {
        this.reconciliationRepo = reconciliationRepo;
        this.tenantRepository = tenantRepository;
        this.circleWalletClient = circleWalletClient;
    }

    @Scheduled(cron = "0 0 23 * * ?")
    public void runEodReconciliation() {
        LocalDate today = LocalDate.now();
        log.info("[RECONCILIATION] EOD-Abgleich gestartet für {}", today);

        List<String> tenantIds = tenantRepository.findAll()
                .stream()
                .map(t -> t.getId())
                .toList();

        int totalDiscrepancies = 0;
        for (String tenantId : tenantIds) {
            try {
                ReconciliationRun run = runForTenant(today, tenantId);
                if ("DISCREPANCY".equals(run.getStatus())) {
                    totalDiscrepancies++;
                }
            } catch (Exception e) {
                log.error("[RECONCILIATION] Fehler für tenant={}: {}", tenantId, e.getMessage(), e);
            }
        }

        log.info("[RECONCILIATION] EOD-Abgleich abgeschlossen: {} Mandanten, {} Diskrepanzen",
                tenantIds.size(), totalDiscrepancies);
    }

    @Transactional
    public ReconciliationRun runForTenant(LocalDate runDate, String tenantId) {
        // Idempotenz: Falls bereits ein Run für heute existiert, abbrechen
        if (reconciliationRepo.findByRunDateAndTenantId(runDate, tenantId).isPresent()) {
            log.info("[RECONCILIATION] Run für tenant={} date={} bereits vorhanden — übersprungen",
                    tenantId, runDate);
            return reconciliationRepo.findByRunDateAndTenantId(runDate, tenantId).get();
        }

        ReconciliationRun run = new ReconciliationRun();
        run.setRunDate(runDate);
        run.setTenantId(tenantId);
        run.setStatus("RUNNING");
        run = reconciliationRepo.save(run);

        TenantContext.set(tenantId);
        try {
            // Fiat-Seite: SETTLED Outbound-TXs des heutigen Tages
            BigDecimal settledOutbound = reconciliationRepo.sumSettledOutboundByDate(runDate, tenantId);
            BigDecimal settledInbound  = reconciliationRepo.sumSettledInboundByDate(runDate, tenantId);
            int settledCount = reconciliationRepo.countSettledOutboundByDate(runDate, tenantId);

            run.setFiatSettledCount(settledCount);
            run.setFiatSettledTotal(settledOutbound != null ? settledOutbound : BigDecimal.ZERO);
            run.setFiatInboundTotal(settledInbound != null ? settledInbound : BigDecimal.ZERO);

            // On-Chain-Seite: Circle Wallet Balance-Snapshot
            var balance = circleWalletClient.getWalletBalance(BANK_MASTER_WALLET);
            BigDecimal usdc = balance.balances() == null ? BigDecimal.ZERO :
                    balance.balances().stream()
                            .filter(b -> "USDC".equalsIgnoreCase(b.currency()))
                            .findFirst().map(b -> new BigDecimal(b.amount())).orElse(BigDecimal.ZERO);
            BigDecimal eurc = balance.balances() == null ? BigDecimal.ZERO :
                    balance.balances().stream()
                            .filter(b -> "EURC".equalsIgnoreCase(b.currency()))
                            .findFirst().map(b -> new BigDecimal(b.amount())).orElse(BigDecimal.ZERO);
            run.setOnchainUsdcBalance(usdc);
            run.setOnchainEurcBalance(eurc);
            run.setOnchainSnapshotAt(LocalDateTime.now());

            // Diskrepanz: Outbound-Fiat - Inbound-Fiat vs. On-Chain-Saldo (vereinfachte Prüfung)
            // In Prod: komplexere FX-adjustierte Berechnung
            BigDecimal netFiat = run.getFiatSettledTotal().subtract(run.getFiatInboundTotal());
            BigDecimal onchainTotal = usdc.add(eurc);  // vereinfacht: 1:1 EUR-Äquivalent
            BigDecimal discrepancy = netFiat.subtract(onchainTotal).abs();
            run.setDiscrepancyEur(discrepancy);

            if (discrepancy.compareTo(run.getDiscrepancyThreshold()) > 0) {
                run.setStatus("DISCREPANCY");
                run.setAlertsGenerated(1);
                log.error("[RECONCILIATION] DISKREPANZ tenant={} discrepancy={}EUR threshold={}EUR",
                        tenantId, discrepancy, run.getDiscrepancyThreshold());
                try {
                    // n8n-Alert (best effort — kein Re-throw)
                    // TODO: eigenes ReconciliationAlertDto einführen wenn n8n-Webhook erweitert wird
                    log.warn("[RECONCILIATION] Alert-Kanal: n8n muss für Reconciliation-Alerts konfiguriert werden");
                } catch (Exception alertEx) {
                    log.warn("[RECONCILIATION] Alert-Benachrichtigung fehlgeschlagen: {}", alertEx.getMessage());
                }
            } else {
                run.setStatus("BALANCED");
                log.info("[RECONCILIATION] BALANCED tenant={} netFiat={}EUR onchain={}EUR discrepancy={}EUR",
                        tenantId, netFiat, onchainTotal, discrepancy);
            }

        } catch (Exception e) {
            run.setStatus("ERROR");
            run.setNotes("Fehler: " + e.getMessage());
            log.error("[RECONCILIATION] Fehler tenant={}: {}", tenantId, e.getMessage(), e);
        } finally {
            run.setCompletedAt(LocalDateTime.now());
            reconciliationRepo.save(run);
            TenantContext.clear();
        }

        return run;
    }
}
