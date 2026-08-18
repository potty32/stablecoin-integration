-- V9: Inbound Stablecoin Processing — neue TransactionStatus-Werte
-- Ersetzt den CHECK-Constraint aus V5 (11 Werte) durch 15 Werte (4 neue Inbound-Zustände)

ALTER TABLE stablecoin_transaction
    DROP CONSTRAINT stablecoin_transaction_status_check;

ALTER TABLE stablecoin_transaction
    ADD CONSTRAINT stablecoin_transaction_status_check
    CHECK (status IN (
        'CREATED', 'PENDING_APPROVAL', 'APPROVED', 'REJECTED', 'EXPIRED',
        'COMPLIANCE_CHECKED', 'FUNDS_HELD', 'SUBMITTED', 'SETTLED', 'REDEEMED', 'FAILED',
        'INCOMING', 'COMPLIANCE_PENDING', 'COMPLIANCE_APPROVED', 'COMPLIANCE_REJECTED'
    ));
