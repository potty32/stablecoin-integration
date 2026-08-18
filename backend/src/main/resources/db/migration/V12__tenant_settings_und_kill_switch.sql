-- V12: G-03 Mandantenspezifische Konfiguration + G-07 Kill Switch
-- tenant_settings: Preise, Limits, Feature-Flags pro Mandant
-- system_control: Globaler Emergency-Stop (DORA Art. 17, §25a KWG)

-- ============================================================
-- tenant_settings — mandantenspezifische Parameter
-- ============================================================
CREATE TABLE tenant_settings (
    tenant_id               VARCHAR(50)   PRIMARY KEY REFERENCES tenant(id),

    -- Preisstruktur (ersetzt globale @Value-Properties)
    fx_spread_b2b           DECIMAL(8,6)  NOT NULL DEFAULT 0.001500,
    fx_spread_b2c           DECIMAL(8,6)  NOT NULL DEFAULT 0.001500,
    fee_flat_b2b_eur        DECIMAL(18,6) NOT NULL DEFAULT 2.500000,
    fee_flat_b2c_eur        DECIMAL(18,6) NOT NULL DEFAULT 0.500000,
    remittance_fee_eur      DECIMAL(18,6) NOT NULL DEFAULT 0.500000,
    micropayment_fee_eur    DECIMAL(18,6) NOT NULL DEFAULT 0.100000,

    -- Vier-Augen-Schwelle (ersetzt app.security.approval-threshold)
    approval_threshold_b2b  DECIMAL(18,6) NOT NULL DEFAULT 25000.000000,

    -- Transaktionslimits (ergänzen customer_account-Felder)
    tx_limit_single_b2b     DECIMAL(18,6) NOT NULL DEFAULT 25000.000000,
    tx_limit_daily_b2b      DECIMAL(18,6) NOT NULL DEFAULT 2000000.000000,
    tx_limit_single_b2c     DECIMAL(18,6) NOT NULL DEFAULT 5000.000000,
    tx_limit_daily_b2c      DECIMAL(18,6) NOT NULL DEFAULT 10000.000000,

    -- Rate-Quote und Slippage-Schutz
    rate_quote_validity_secs INT           NOT NULL DEFAULT 60,
    slippage_tolerance_bps   INT           NOT NULL DEFAULT 100,

    -- Erlaubte Währungen & Blockchains (CSV)
    allowed_currencies      VARCHAR(50)   NOT NULL DEFAULT 'USDC,EURC',
    allowed_blockchains     VARCHAR(100)  NOT NULL DEFAULT 'POLYGON',

    -- Geo-Compliance: ISO-3166 Ländercodes (CSV), NULL = keine Sperre
    blocked_countries       TEXT,

    -- Feature-Flags
    enable_yield            BOOLEAN       NOT NULL DEFAULT TRUE,
    enable_bulk_payments    BOOLEAN       NOT NULL DEFAULT TRUE,

    -- Kill Switch pro Mandant (G-07)
    kill_switch_active      BOOLEAN       NOT NULL DEFAULT FALSE,
    kill_switch_reason      TEXT,
    kill_switch_by          VARCHAR(100),
    kill_switch_at          TIMESTAMP,

    updated_at              TIMESTAMP     NOT NULL DEFAULT NOW()
);

-- Seed: alle bestehenden Tenants erhalten Default-Werte
INSERT INTO tenant_settings (tenant_id) VALUES
    ('tenant-kleine-vb'),
    ('tenant-grosse-vb'),
    ('tenant-marktbank'),
    ('tenant-default');

-- ============================================================
-- system_control — globaler Emergency-Stop
-- ============================================================
CREATE TABLE system_control (
    scope               VARCHAR(50)  PRIMARY KEY,  -- 'GLOBAL' oder 'TENANT:<id>'
    kill_switch_active  BOOLEAN      NOT NULL DEFAULT FALSE,
    kill_switch_reason  TEXT,
    kill_switch_by      VARCHAR(100),
    kill_switch_at      TIMESTAMP,
    updated_at          TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- Globaler Schalter (initial deaktiviert)
INSERT INTO system_control (scope) VALUES ('GLOBAL');
