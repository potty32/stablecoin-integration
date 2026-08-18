-- V13: G-02 Kapitalertragsteuer — Audit-Nachweis für AtruviaTaxClient-Meldungen
-- Freistellungsaufträge und Steuerberechnung liegen beim Atruvia Tax Engine (Drittsystem).
-- Diese Tabelle speichert nur das Ergebnis der Drittsystem-Anfrage (Audit Trail).

CREATE TABLE tax_event (
    id                  UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    yield_position_id   UUID         NOT NULL REFERENCES yield_position(id),
    redeem_tx_id        UUID         NOT NULL REFERENCES stablecoin_transaction(id),
    customer_account_id UUID         NOT NULL REFERENCES customer_account(id),
    tax_year            SMALLINT     NOT NULL,

    -- Vom Drittsystem gemeldete Werte
    gross_yield_eur     DECIMAL(18,6) NOT NULL,
    tax_withheld_eur    DECIMAL(18,6) NOT NULL,
    net_payout_eur      DECIMAL(18,6) NOT NULL,
    tax_reference_id    VARCHAR(100),              -- Referenz-ID beim Atruvia Tax Engine
    tax_status          VARCHAR(20)  NOT NULL      -- TAX_APPLIED | FSA_COVERED | PARTIAL_FSA
        CHECK (tax_status IN ('TAX_APPLIED', 'FSA_COVERED', 'PARTIAL_FSA')),

    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    tenant_id           VARCHAR(50)  NOT NULL REFERENCES tenant(id)
);

CREATE INDEX idx_tax_event_customer_year ON tax_event(customer_account_id, tax_year);
CREATE INDEX idx_tax_event_tenant       ON tax_event(tenant_id);
