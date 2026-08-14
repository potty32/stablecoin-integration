CREATE TABLE customer_account (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id VARCHAR(50) NOT NULL,
    iban VARCHAR(34) UNIQUE NOT NULL,
    wallet_address VARCHAR(100),
    customer_type VARCHAR(10) NOT NULL CHECK (customer_type IN ('B2B', 'B2C')),
    kyc_tier VARCHAR(10) NOT NULL CHECK (kyc_tier IN ('TIER_1', 'TIER_2', 'TIER_3')),
    tx_limit_single DECIMAL(18,6) NOT NULL DEFAULT 25000.00,
    tx_limit_daily DECIMAL(18,6) NOT NULL DEFAULT 100000.00,
    status VARCHAR(15) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'SUSPENDED', 'BLOCKED')),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_customer_account_customer_id ON customer_account(customer_id);
CREATE INDEX idx_customer_account_iban ON customer_account(iban);

CREATE TABLE rate_quote (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_account_id UUID NOT NULL REFERENCES customer_account(id),
    source_currency VARCHAR(10) NOT NULL,
    target_currency VARCHAR(10) NOT NULL CHECK (target_currency IN ('USDC', 'EURC')),
    source_amount DECIMAL(18,6) NOT NULL,
    quoted_rate DECIMAL(18,8) NOT NULL,
    spread_applied DECIMAL(8,6) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    status VARCHAR(10) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'USED', 'EXPIRED')),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_rate_quote_customer ON rate_quote(customer_account_id);
CREATE INDEX idx_rate_quote_status ON rate_quote(status);

CREATE TABLE stablecoin_transaction (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    idempotency_key VARCHAR(100) UNIQUE NOT NULL,
    customer_account_id UUID NOT NULL REFERENCES customer_account(id),
    type VARCHAR(20) NOT NULL CHECK (type IN ('OUTBOUND','INBOUND','BULK','P2P','REMITTANCE','YIELD_DEPOSIT')),
    currency VARCHAR(10) NOT NULL CHECK (currency IN ('USDC','EURC')),
    amount_fiat DECIMAL(18,6) NOT NULL,
    amount_stablecoin DECIMAL(18,6),
    fx_rate DECIMAL(18,8),
    fx_spread DECIMAL(8,6),
    transaction_fee DECIMAL(18,6),
    gas_cost DECIMAL(18,8),
    gross_revenue DECIMAL(18,6),
    source_wallet VARCHAR(100),
    destination_wallet VARCHAR(100) NOT NULL,
    rate_quote_id UUID REFERENCES rate_quote(id),
    circle_transaction_id VARCHAR(100),
    blockchain_hash VARCHAR(100),
    status VARCHAR(25) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','AWAITING_APPROVAL','COMPLIANCE_CHECK','PROCESSING','SETTLED','FAILED','BLOCKED')),
    failure_reason TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    settled_at TIMESTAMP
);

CREATE INDEX idx_stx_customer ON stablecoin_transaction(customer_account_id);
CREATE INDEX idx_stx_status ON stablecoin_transaction(status);
CREATE INDEX idx_stx_idempotency ON stablecoin_transaction(idempotency_key);
CREATE INDEX idx_stx_created ON stablecoin_transaction(created_at DESC);

CREATE TABLE approval_workflow (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id UUID UNIQUE NOT NULL REFERENCES stablecoin_transaction(id),
    initiator_id VARCHAR(100) NOT NULL,
    approver_id VARCHAR(100),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING_APPROVAL' CHECK (status IN ('PENDING_APPROVAL','APPROVED','REJECTED','EXPIRED')),
    expires_at TIMESTAMP NOT NULL,
    approved_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_approval_transaction ON approval_workflow(transaction_id);
CREATE INDEX idx_approval_status ON approval_workflow(status);

CREATE TABLE address_book (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_account_id UUID NOT NULL REFERENCES customer_account(id),
    label VARCHAR(100) NOT NULL,
    wallet_address VARCHAR(100) NOT NULL,
    currency VARCHAR(10) NOT NULL CHECK (currency IN ('USDC','EURC')),
    risk_score VARCHAR(10) NOT NULL DEFAULT 'LOW' CHECK (risk_score IN ('LOW','MEDIUM','HIGH')),
    verified_at TIMESTAMP NOT NULL DEFAULT NOW(),
    status VARCHAR(10) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','REVOKED')),
    UNIQUE(customer_account_id, wallet_address)
);

CREATE INDEX idx_address_book_customer ON address_book(customer_account_id);

CREATE TABLE outbox_message (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id UUID NOT NULL,
    aggregate_type VARCHAR(50) NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(10) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','SENT','FAILED')),
    attempts INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    processed_at TIMESTAMP
);

CREATE INDEX idx_outbox_status ON outbox_message(status) WHERE status = 'PENDING';
CREATE INDEX idx_outbox_transaction ON outbox_message(transaction_id);

CREATE TABLE phone_alias (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    phone_number_hash VARCHAR(64) UNIQUE NOT NULL,
    wallet_address VARCHAR(100) NOT NULL,
    customer_account_id UUID NOT NULL REFERENCES customer_account(id),
    verified_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- INSERT-ONLY: no UPDATE or DELETE allowed (enforced at application level)
CREATE TABLE audit_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_type VARCHAR(50) NOT NULL,
    entity_id UUID NOT NULL,
    action VARCHAR(50) NOT NULL,
    previous_state JSONB,
    new_state JSONB,
    user_id VARCHAR(100),
    ip_address VARCHAR(45),
    trace_id VARCHAR(64),
    timestamp TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_entity ON audit_log(entity_type, entity_id);
CREATE INDEX idx_audit_timestamp ON audit_log(timestamp DESC);

-- Dev seed data
INSERT INTO customer_account (id, customer_id, iban, wallet_address, customer_type, kyc_tier, tx_limit_single, tx_limit_daily, status)
VALUES
    ('a0000000-0000-0000-0000-000000000001', 'cust-b2b-001', 'DE89370400440532013000', '0xBankB2BWallet000000000000000000000000001', 'B2B', 'TIER_3', 500000.00, 2000000.00, 'ACTIVE'),
    ('a0000000-0000-0000-0000-000000000002', 'cust-b2c-001', 'DE27200400600532013001', '0xBankB2CWallet000000000000000000000000002', 'B2C', 'TIER_2', 5000.00, 10000.00, 'ACTIVE');
