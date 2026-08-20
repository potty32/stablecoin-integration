-- V22: F-02-Seed-Korrektur — Kunden-Transaktionslimits der Dev-Seed-Accounts anpassen
--
-- Hintergrund: V1 setzte tx_limit_single = 25.000 EUR für alle Accounts.
-- Das war für den alten (uninforcierten) Code kein Problem, aber mit der neuen
-- Limit-Enforcement (F-02) bedeutet tx_limit_single = 25.000 das der 4-Augen-
-- Schwellwert (ebenfalls 25.000) auch das absolute Maximum ist — kein Transfer
-- über 25k wäre möglich, obwohl der 4-Augen-Prozess genau dafür gedacht ist.
--
-- Korrekte Semantik:
--   customer_account.tx_limit_single  = Kunden-spezifisches Limit (darf < Tenant-Max sein)
--   tenant_settings.tx_limit_single   = Bank-Obergrenze (wird in V21 auf 500k gesetzt)
--   tenant_settings.approval_threshold = Ab hier: 4-Augen (25k)
--
-- B2B-Kunden: Limit auf 200.000 EUR (unter dem 500k Tenant-Max, über der 25k Schwelle).
-- B2C-Kunden: Limit bleibt bei 5.000 EUR.

UPDATE customer_account
    SET tx_limit_single = 200000.000000
    WHERE customer_type = 'B2B'
      AND tx_limit_single = 25000.000000;
