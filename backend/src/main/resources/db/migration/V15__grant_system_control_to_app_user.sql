-- V15: Zugriffsrechte für neue Tabellen an stablecoin_app-User (G-01..G-07)
-- G-07: system_control muss von stablecoin_app lesbar sein (KillSwitchFilter)
-- G-03: tenant_settings muss von stablecoin_app lesbar und änderbar sein
-- G-02: tax_event braucht INSERT/SELECT für Audit-Nachweis
-- G-04: reconciliation_run braucht INSERT/SELECT/UPDATE
-- Grants werden nur ausgeführt wenn stablecoin_app-Role existiert (Railway-kompatibel)

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'stablecoin_app') THEN
        GRANT SELECT, UPDATE ON system_control TO stablecoin_app;
        GRANT SELECT, INSERT, UPDATE ON tenant_settings TO stablecoin_app;
        GRANT SELECT, INSERT ON tax_event TO stablecoin_app;
        GRANT SELECT, INSERT, UPDATE ON reconciliation_run TO stablecoin_app;
    END IF;
END $$;
