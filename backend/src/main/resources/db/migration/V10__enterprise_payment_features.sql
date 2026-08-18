-- V10: Enterprise Payment Features
-- 1. R-Transaktionen: parent_transaction_id für Audit Trail
-- 2. Neuer Typ INBOUND_RETURN (Retouren-Zahlung)
-- 3. Neue Status UNASSIGNED (Sammelkonto) und RETURNED (Retoure abgeschlossen)
-- 4. Sammelkonto-Eintrag für nicht zuordenbare Geldeingänge

-- ============================================================
-- 1. parent_transaction_id — verknüpft Retoure mit Original-TX
-- ============================================================
ALTER TABLE stablecoin_transaction
    ADD COLUMN parent_transaction_id UUID REFERENCES stablecoin_transaction(id);

CREATE INDEX idx_stx_parent_tx ON stablecoin_transaction(parent_transaction_id)
    WHERE parent_transaction_id IS NOT NULL;

-- ============================================================
-- 2. INBOUND_RETURN als neuer TransactionType
-- ============================================================
ALTER TABLE stablecoin_transaction DROP CONSTRAINT stablecoin_transaction_type_check;
ALTER TABLE stablecoin_transaction ADD CONSTRAINT stablecoin_transaction_type_check
    CHECK (type IN (
        'OUTBOUND', 'INBOUND', 'BULK', 'P2P', 'REMITTANCE',
        'YIELD_DEPOSIT', 'YIELD_REDEEM', 'INBOUND_RETURN'
    ));

-- ============================================================
-- 3. UNASSIGNED + RETURNED als neue TransactionStatus-Werte
-- ============================================================
ALTER TABLE stablecoin_transaction DROP CONSTRAINT stablecoin_transaction_status_check;
ALTER TABLE stablecoin_transaction ADD CONSTRAINT stablecoin_transaction_status_check
    CHECK (status IN (
        'CREATED', 'PENDING_APPROVAL', 'APPROVED', 'REJECTED', 'EXPIRED',
        'COMPLIANCE_CHECKED', 'FUNDS_HELD', 'SUBMITTED', 'SETTLED',
        'REDEEMED', 'FAILED',
        'INCOMING', 'COMPLIANCE_PENDING', 'COMPLIANCE_APPROVED', 'COMPLIANCE_REJECTED',
        'UNASSIGNED', 'RETURNED'
    ));

-- ============================================================
-- 4. Sammelkonto für nicht zuordenbare Stablecoin-Eingänge
-- tenant_id = 'tenant-default' → sichtbar im Dev/Default-Kontext
-- customer_id = 'unassigned-funds' → findByCustomerId-Lookup
-- ============================================================
INSERT INTO customer_account
    (id, customer_id, iban, wallet_address, customer_type, kyc_tier,
     tx_limit_single, tx_limit_daily, status, created_at, updated_at, tenant_id)
VALUES
    ('00000000-0000-0000-0000-000000000099',
     'unassigned-funds',
     'SYSTEM-COLLECTION-0000000000000000',
     'SYSTEM_COLLECTION_WALLET',
     'B2B', 'TIER_3',
     99999999.000000, 99999999.000000,
     'ACTIVE',
     NOW(), NOW(),
     'tenant-default');
