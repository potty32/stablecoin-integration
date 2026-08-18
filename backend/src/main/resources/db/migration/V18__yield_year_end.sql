-- V18: G-15 Jahresabschluss Yield-Bewertung + tax_event.redeem_tx_id nullable machen
ALTER TABLE tax_event ALTER COLUMN redeem_tx_id DROP NOT NULL;
-- Offene ACTIVE YieldPositions müssen zum 31.12. steuerlich bewertet werden
-- (EStG §11 Realisationsprinzip, HGB §252 Jahresabschluss)

ALTER TABLE yield_position
    ADD COLUMN year_end_valuation_eur DECIMAL(18,6),  -- Barwert zum 31.12.
    ADD COLUMN year_end_tax_event_id  UUID REFERENCES tax_event(id), -- Referenz zur Tax-Meldung
    ADD COLUMN last_valued_year       SMALLINT;        -- Jahr der letzten Bewertung (Duplikat-Schutz)

CREATE INDEX idx_yield_year_valued ON yield_position(last_valued_year, status);
