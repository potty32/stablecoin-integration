-- V20: UC-33–35 Delivery-versus-Payment (DvP) Escrow Engine + Multi-Token-Unterstützung
-- Eliminiert Herstatt-Risiko bei tokenisierten Wertpapiergeschäften (DZ Bank Konsortium, Deka, Union)
-- RLS aktiviert: Mandantentrennung über tenant_id (identisches Muster wie V8)

CREATE TABLE dvp_escrow (
    id                    UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_account_id   UUID         NOT NULL REFERENCES customer_account(id),
    amount_fiat           DECIMAL(18,6) NOT NULL,
    amount_stablecoin     DECIMAL(18,6) NOT NULL,
    currency              VARCHAR(10)  NOT NULL,
    status                VARCHAR(20)  NOT NULL
        CHECK (status IN ('ESCROWED', 'SETTLED', 'CANCELLED')),
    settlement_wallet     VARCHAR(100) NOT NULL,
    securities_isin       VARCHAR(20)  NOT NULL,
    securities_amount     DECIMAL(18,6) NOT NULL,
    escrow_reference      VARCHAR(100) NOT NULL UNIQUE,
    securities_system_id  VARCHAR(50),
    hold_id               VARCHAR(100),
    fee_amount            DECIMAL(18,6),
    blockchain_hash       VARCHAR(100),
    locked_at             TIMESTAMP    NOT NULL DEFAULT NOW(),
    settled_at            TIMESTAMP,
    cancelled_at          TIMESTAMP,
    cancellation_reason   TEXT,
    tenant_id             VARCHAR(50)  NOT NULL REFERENCES tenant(id),
    created_at            TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_dvp_escrow_reference ON dvp_escrow(escrow_reference);
CREATE INDEX idx_dvp_escrow_tenant    ON dvp_escrow(tenant_id);
CREATE INDEX idx_dvp_escrow_customer  ON dvp_escrow(customer_account_id, status);

-- RLS: Mandantentrennnung (identisches Muster wie V8)
ALTER TABLE dvp_escrow ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_policy ON dvp_escrow
    USING (tenant_id = current_setting('app.current_tenant', true));

-- App-User-Grants
GRANT SELECT, INSERT, UPDATE ON dvp_escrow TO stablecoin_app;
