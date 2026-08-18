-- V17: G-12 FATF Recommendation 16 (Travel Rule)
-- Cross-Border Transfers >15.000 EUR erfordern vollständige Begünstigteninformationen

-- Felder auf stablecoin_transaction
ALTER TABLE stablecoin_transaction
    ADD COLUMN beneficiary_name         VARCHAR(200),
    ADD COLUMN beneficiary_address      TEXT,
    ADD COLUMN beneficiary_account_id   VARCHAR(50),   -- IBAN oder externe Wallet
    ADD COLUMN originator_name          VARCHAR(200),
    ADD COLUMN travel_rule_required     BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN travel_rule_completed_at TIMESTAMP;

-- Konfiguration in tenant_settings
ALTER TABLE tenant_settings
    ADD COLUMN travel_rule_enabled       BOOLEAN      NOT NULL DEFAULT FALSE,
    ADD COLUMN travel_rule_threshold_eur DECIMAL(18,6) NOT NULL DEFAULT 15000.000000;
