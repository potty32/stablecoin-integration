CREATE TABLE institutional_address_book (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    label        VARCHAR(100) NOT NULL,
    wallet_address VARCHAR(100) NOT NULL,
    currency     VARCHAR(10)  NOT NULL CHECK (currency IN ('USDC', 'EURC')),
    risk_score   VARCHAR(10)  NOT NULL DEFAULT 'LOW' CHECK (risk_score IN ('LOW', 'MEDIUM', 'HIGH')),
    verified_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    status       VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'REVOKED')),
    created_by   VARCHAR(100),
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    UNIQUE (wallet_address, currency)
);

CREATE INDEX idx_inst_addr_book_status ON institutional_address_book (status);
CREATE INDEX idx_inst_addr_book_wallet ON institutional_address_book (wallet_address);
