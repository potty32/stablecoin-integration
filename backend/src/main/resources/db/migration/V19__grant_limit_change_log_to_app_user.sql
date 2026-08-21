-- V19: Fehlende Grants für limit_change_log (erstellt in V16, nicht in V15 berücksichtigt)
-- V8 greift nur auf Tabellen zu, die zum Migrationszeitpunkt existieren.
-- Tabellen aus V10+ müssen explizit nachgepflegt werden.

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'stablecoin_app') THEN
        GRANT SELECT, INSERT, UPDATE ON limit_change_log TO stablecoin_app;
    END IF;
END $$;
