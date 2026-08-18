package de.atruvia.stablecoin.exception;

/**
 * G-13: Tatsächliche Erfolgsquote eines Bulk-Payment-Batches unterschreitet
 * die in TenantSettings konfigurierte Mindest-Erfolgsquote.
 * → HTTP 422 UNPROCESSABLE_ENTITY (BIZ_006)
 */
public class BulkPaymentThresholdException extends RuntimeException {
    private final int successCount;
    private final int totalCount;
    private final double minSuccessRate;

    public BulkPaymentThresholdException(int successCount, int totalCount, double minSuccessRate) {
        super(String.format("Bulk-Payment: Erfolgsquote %.0f%% (%d/%d) unter Mindest-%.0f%%",
                (double) successCount / totalCount * 100, successCount, totalCount, minSuccessRate * 100));
        this.successCount = successCount;
        this.totalCount = totalCount;
        this.minSuccessRate = minSuccessRate;
    }

    public int getSuccessCount() { return successCount; }
    public int getTotalCount() { return totalCount; }
    public double getMinSuccessRate() { return minSuccessRate; }
}
