-- 1. stablecoin_transaction type-Constraint um YIELD_REDEEM erweitern
ALTER TABLE stablecoin_transaction DROP CONSTRAINT stablecoin_transaction_type_check;
ALTER TABLE stablecoin_transaction ADD CONSTRAINT stablecoin_transaction_type_check
    CHECK (type IN ('OUTBOUND', 'INBOUND', 'BULK', 'P2P', 'REMITTANCE', 'YIELD_DEPOSIT', 'YIELD_REDEEM'));

-- 2. yield_position Tabelle anlegen
CREATE TABLE yield_position (
    id                     UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_account_id    UUID        NOT NULL REFERENCES customer_account(id),
    principal              DECIMAL(18,6) NOT NULL,
    interest_rate          DECIMAL(10,6) NOT NULL DEFAULT 0.035,
    deposited_at           TIMESTAMP   NOT NULL DEFAULT NOW(),
    closed_at              TIMESTAMP,
    status                 VARCHAR(10) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'CLOSED')),
    deposit_transaction_id UUID        REFERENCES stablecoin_transaction(id)
);

CREATE INDEX idx_yield_position_customer ON yield_position (customer_account_id);
CREATE INDEX idx_yield_position_status   ON yield_position (status);
