-- V21: F-02-Fix — Trennung von Vier-Augen-Schwelle und absolutem Einzeltransaktion-Cap
-- approvalThresholdB2b (25.000 EUR): Vier-Augen-Trigger — Transaktion geht in Review-Workflow
-- txLimitSingleB2b      (500.000 EUR): Absoluter Cap (GwG §3) — auch mit Approval nicht überschreitbar
--
-- Korrektur: txLimitSingleB2b war identisch mit approvalThresholdB2b (25k), was jede
-- Transaktion > 25k blockierte — auch solche die regulär durch Vier-Augen-Prozess gehen.

ALTER TABLE tenant_settings
    ALTER COLUMN tx_limit_single_b2b SET DEFAULT 500000.000000;

UPDATE tenant_settings
    SET tx_limit_single_b2b = 500000.000000
    WHERE tx_limit_single_b2b = 25000.000000;
