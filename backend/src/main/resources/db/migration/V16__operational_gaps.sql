-- V16: Operative Gaps G-08, G-09, G-13, G-14

-- ============================================================
-- G-14: Telefonnummer-Hash-Algorithmus tracken (Datenschutz)
-- ============================================================
ALTER TABLE phone_alias
    ADD COLUMN phone_hash_algorithm VARCHAR(30) NOT NULL DEFAULT 'HMAC_SHA256_V1';

-- ============================================================
-- G-08: Audit-Trail für manuelle Limit-Änderungen
-- ============================================================
CREATE TABLE limit_change_log (
    id                  UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_account_id UUID         NOT NULL REFERENCES customer_account(id),
    changed_by          VARCHAR(100) NOT NULL,
    field_name          VARCHAR(50)  NOT NULL, -- TX_LIMIT_SINGLE | TX_LIMIT_DAILY
    old_value           DECIMAL(18,6),
    new_value           DECIMAL(18,6),
    reason              TEXT,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    tenant_id           VARCHAR(50)  NOT NULL REFERENCES tenant(id)
);
CREATE INDEX idx_limit_log_account ON limit_change_log(customer_account_id);

-- ============================================================
-- G-13: Bulk-Payment Mindest-Erfolgsquote in TenantSettings
-- ============================================================
ALTER TABLE tenant_settings
    ADD COLUMN bulk_min_success_rate DECIMAL(5,2) NOT NULL DEFAULT 0.00,
    -- 0.00 = kein Limit (Rückwärtskompatibilität), 0.50 = mind. 50%
    ADD COLUMN bulk_dry_run_enabled  BOOLEAN      NOT NULL DEFAULT FALSE;

-- ============================================================
-- G-09: Idempotenz-Key Ablaufzeit (PSD2: 30-Tage-Replay-Schutz)
-- ============================================================
ALTER TABLE stablecoin_transaction
    ADD COLUMN idempotency_expires_at TIMESTAMP;

-- Bestehende Einträge: 30 Tage ab Erstellung
UPDATE stablecoin_transaction
SET idempotency_expires_at = created_at + INTERVAL '30 days'
WHERE idempotency_expires_at IS NULL;
