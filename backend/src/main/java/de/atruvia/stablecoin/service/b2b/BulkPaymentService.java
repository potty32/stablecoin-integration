package de.atruvia.stablecoin.service.b2b;

import de.atruvia.stablecoin.config.TenantContext;
import de.atruvia.stablecoin.dto.request.b2b.InitiateTransferRequest;
import de.atruvia.stablecoin.dto.response.BulkPaymentResult;
import de.atruvia.stablecoin.dto.response.BulkRowResult;
import de.atruvia.stablecoin.dto.response.TransactionResponse;
import de.atruvia.stablecoin.entity.TenantSettings;
import de.atruvia.stablecoin.entity.StablecoinCurrency;
import de.atruvia.stablecoin.exception.BulkPaymentThresholdException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class BulkPaymentService {

    private static final Logger log = LoggerFactory.getLogger(BulkPaymentService.class);

    /**
     * Accepts Ethereum-style wallet addresses (0x + 40 hex chars).
     * Extend pattern if other address formats are required.
     */
    private static final Pattern ETH_ADDRESS = Pattern.compile("^0x[0-9a-fA-F]{40}$");

    private final B2bTransferService transferService;
    private final TenantSettingsService tenantSettingsService;

    public BulkPaymentService(B2bTransferService transferService,
                               TenantSettingsService tenantSettingsService) {
        this.transferService = transferService;
        this.tenantSettingsService = tenantSettingsService;
    }

    /**
     * Parses a CSV file and creates one transfer per data row.
     * CSV format (per row): destinationWallet,amountEur,currency,reference
     *
     * @param file        multipart CSV upload
     * @param sourceIban  IBAN of the source account
     * @param initiatorId authenticated user id / customer-id
     * @return aggregated result with per-row outcome
     */
    public BulkPaymentResult process(MultipartFile file, String sourceIban, String initiatorId) {
        List<BulkRowResult> rows = new ArrayList<>();
        int successful = 0;
        int failed = 0;
        int lineNumber = 0;
        int dataRowNumber = 0;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {

            String line;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                String trimmed = line.trim();

                // Skip blank lines
                if (trimmed.isEmpty()) {
                    continue;
                }

                // Skip header row (case-insensitive check for first column name)
                if (lineNumber == 1 && trimmed.toLowerCase().startsWith("destinationwallet")) {
                    continue;
                }

                dataRowNumber++;

                String[] parts = trimmed.split(",", -1);
                if (parts.length < 4) {
                    rows.add(errorRow(dataRowNumber, null, null,
                            "Invalid CSV format: expected 4 columns, got " + parts.length));
                    failed++;
                    continue;
                }

                String destinationWallet = parts[0].trim();
                String amountStr        = parts[1].trim();
                String currencyStr      = parts[2].trim();
                String reference        = parts[3].trim();

                // Validate wallet address
                if (destinationWallet.isEmpty() || !ETH_ADDRESS.matcher(destinationWallet).matches()) {
                    rows.add(errorRow(dataRowNumber, destinationWallet, amountStr,
                            "Invalid destination wallet address: '" + destinationWallet + "'"));
                    failed++;
                    continue;
                }

                // Validate amount
                BigDecimal amountEur;
                try {
                    amountEur = new BigDecimal(amountStr);
                    if (amountEur.compareTo(BigDecimal.ZERO) <= 0) {
                        rows.add(errorRow(dataRowNumber, destinationWallet, amountStr,
                                "Amount must be positive, got: " + amountStr));
                        failed++;
                        continue;
                    }
                } catch (NumberFormatException e) {
                    rows.add(errorRow(dataRowNumber, destinationWallet, amountStr,
                            "Invalid amount value: '" + amountStr + "'"));
                    failed++;
                    continue;
                }

                // Validate currency
                StablecoinCurrency currency;
                try {
                    currency = StablecoinCurrency.valueOf(currencyStr.toUpperCase());
                } catch (IllegalArgumentException e) {
                    rows.add(errorRow(dataRowNumber, destinationWallet, amountStr,
                            "Unknown currency: '" + currencyStr + "'"));
                    failed++;
                    continue;
                }

                // Attempt to create the transaction
                try {
                    String idempotencyKey = UUID.randomUUID().toString();
                    InitiateTransferRequest request = new InitiateTransferRequest(
                            sourceIban, destinationWallet, amountEur, currency,
                            null, null, reference, null, null, null);
                    TransactionResponse response = transferService.initiate(idempotencyKey, request, initiatorId);
                    rows.add(new BulkRowResult(dataRowNumber, destinationWallet, amountStr,
                            "OK", "Transaction created", response.transactionId().toString()));
                    successful++;
                    log.info("[BulkPayment] row={} txId={} amount={} EUR destination={}",
                            dataRowNumber, rows.getLast().transactionId(), amountStr, destinationWallet);
                } catch (Exception e) {
                    log.warn("[BulkPayment] row={} failed: {}", dataRowNumber, e.getMessage());
                    rows.add(errorRow(dataRowNumber, destinationWallet, amountStr, e.getMessage()));
                    failed++;
                }
            }
        } catch (Exception e) {
            log.error("[BulkPayment] CSV parsing error: {}", e.getMessage(), e);
            throw new IllegalArgumentException("Failed to parse CSV file: " + e.getMessage(), e);
        }

        BulkPaymentResult result = new BulkPaymentResult(successful + failed, successful, failed, rows);

        // G-13: Mindest-Erfolgsquote prüfen
        TenantSettings settings = tenantSettingsService.get(TenantContext.get());
        double minRate = settings.getBulkMinSuccessRate().doubleValue();
        if (minRate > 0.0 && (successful + failed) > 0) {
            double actualRate = (double) successful / (successful + failed);
            if (actualRate < minRate) {
                log.error("[BulkPayment] Erfolgsquote {:.0f}% < Mindest-{:.0f}% — Batch als THRESHOLD_NOT_MET markiert",
                        actualRate * 100, minRate * 100);
                throw new BulkPaymentThresholdException(successful, successful + failed, minRate);
            }
        }

        return result;
    }

    private BulkRowResult errorRow(int rowNumber, String wallet, String amount, String message) {
        return new BulkRowResult(rowNumber, wallet, amount, "ERROR", message, null);
    }
}
