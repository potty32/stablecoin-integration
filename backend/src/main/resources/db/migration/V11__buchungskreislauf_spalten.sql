-- V11: G-01 Buchungskreislauf-Korrekturen
-- Neue Spalten für brutto-konformes Buchungsmodell (MiCA Art. 23, HGB §246)

ALTER TABLE stablecoin_transaction
    -- Kundendebit inkl. Flat-Gebühr + FX-Spread (ggf. > amountFiat)
    ADD COLUMN gross_debit              DECIMAL(18,6),
    -- Separate Flat-Gebühr (transparent für Reporting)
    ADD COLUMN fee_amount               DECIMAL(18,6),
    -- Kernbank-Buchungsreferenz — gesetzt nach createLedgerBooking(); Basis für Storno
    ADD COLUMN ledger_booking_reference VARCHAR(100),
    -- Slippage-Toleranz in Basispunkten (100 BPS = 1%)
    ADD COLUMN slippage_tolerance_bps   INT          DEFAULT 100,
    -- Quellensteuer einbehalten (von AtruviaTaxClient gemeldet, G-02)
    ADD COLUMN tax_withheld             DECIMAL(18,6);

COMMENT ON COLUMN stablecoin_transaction.gross_debit              IS 'Kundendebit inkl. Gebühren (G-01)';
COMMENT ON COLUMN stablecoin_transaction.fee_amount               IS 'Flat-Gebühr separat ausgewiesen (MiCA Art. 23)';
COMMENT ON COLUMN stablecoin_transaction.ledger_booking_reference IS 'CoreBanking-Buchungsref. für Storno bei FAILED';
COMMENT ON COLUMN stablecoin_transaction.tax_withheld             IS 'Quellensteuer via AtruviaTaxClient (G-02)';
