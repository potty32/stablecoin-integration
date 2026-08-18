-- V14: G-04 Tagesabschluss-Reconciliation
-- Täglicher Soll/Haben-Abgleich zwischen Fiat-Ledger und On-Chain-Salden
-- (AT 7.2 MaRisk, §25a KWG, §238 HGB)

CREATE TABLE reconciliation_run (
    id                    UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    run_date              DATE         NOT NULL,
    tenant_id             VARCHAR(50)  NOT NULL REFERENCES tenant(id),
    status                VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING','RUNNING','BALANCED','DISCREPANCY','ERROR')),

    -- Fiat-Seite (aus stablecoin_transaction, CoreBanking)
    fiat_settled_count    INT          NOT NULL DEFAULT 0,
    fiat_settled_total    DECIMAL(18,6) NOT NULL DEFAULT 0,
    fiat_inbound_total    DECIMAL(18,6) NOT NULL DEFAULT 0,
    fiat_fees_collected   DECIMAL(18,6) NOT NULL DEFAULT 0,

    -- On-Chain-Seite (Circle API Wallet-Snapshot)
    onchain_usdc_balance  DECIMAL(18,6),
    onchain_eurc_balance  DECIMAL(18,6),
    onchain_snapshot_at   TIMESTAMP,

    -- Ergebnis
    discrepancy_eur       DECIMAL(18,6),           -- Positiv = Überschuss Fiat, Negativ = On-Chain-Überhang
    discrepancy_threshold DECIMAL(18,6) NOT NULL DEFAULT 0.010000,  -- 1 Cent Toleranz
    alerts_generated      INT          NOT NULL DEFAULT 0,
    notes                 TEXT,
    completed_at          TIMESTAMP,
    created_at            TIMESTAMP    NOT NULL DEFAULT NOW(),

    UNIQUE (run_date, tenant_id)
);

CREATE INDEX idx_reconciliation_status ON reconciliation_run(status);
CREATE INDEX idx_reconciliation_tenant ON reconciliation_run(tenant_id, run_date DESC);
