-- Approval-Threshold auf Plan-konforme 25.000 EUR setzen
-- Plan-Vorgabe: approvalThresholdEur = 25.000 EUR (aus POST /b2b/transfers Sample-Response)
UPDATE customer_account
SET tx_limit_single = 25000.00
WHERE customer_id = 'cust-b2b-001';
