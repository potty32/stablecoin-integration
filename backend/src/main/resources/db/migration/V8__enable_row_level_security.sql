-- V8: Multi-Tenancy via PostgreSQL Row-Level Security
-- Migration-User: stablecoin (Table-Owner, bypassed RLS automatisch bei ENABLE ohne FORCE)
-- App-User:       stablecoin_app (Nicht-Owner, unterliegt RLS vollständig)

-- ============================================================
-- 1. Tenant-Referenztabelle
-- ============================================================
CREATE TABLE tenant (
    id         VARCHAR(50)  PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    type       VARCHAR(20)  NOT NULL,
    rls_active BOOLEAN      NOT NULL DEFAULT true
);

INSERT INTO tenant (id, name, type, rls_active) VALUES
    ('tenant-kleine-vb', 'Volksbank Kleinstadt eG', 'COOPERATIVE', true),
    ('tenant-grosse-vb', 'Volksbank Metropole eG',  'COOPERATIVE', true),
    ('tenant-marktbank', 'Marktbank AG',             'BANK',        true),
    ('tenant-default',   'Default Dev Tenant',       'DEV',         true);

-- ============================================================
-- 2. App-User Grants (nur wenn stablecoin_app-Role existiert)
--    Auf Railway gibt es nur einen DB-User → Grants werden übersprungen,
--    Table-Owner bypassed RLS ohnehin automatisch.
-- ============================================================
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'stablecoin_app') THEN
        GRANT USAGE ON SCHEMA public TO stablecoin_app;
        GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO stablecoin_app;
        GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO stablecoin_app;
    END IF;
END $$;

-- ============================================================
-- 3. tenant_id-Spalten (nullable -> backfill -> NOT NULL)
-- ============================================================
ALTER TABLE customer_account       ADD COLUMN tenant_id VARCHAR(50) REFERENCES tenant(id);
ALTER TABLE stablecoin_transaction ADD COLUMN tenant_id VARCHAR(50) REFERENCES tenant(id);
ALTER TABLE address_book           ADD COLUMN tenant_id VARCHAR(50) REFERENCES tenant(id);
ALTER TABLE yield_position         ADD COLUMN tenant_id VARCHAR(50) REFERENCES tenant(id);
ALTER TABLE audit_log              ADD COLUMN tenant_id VARCHAR(50) REFERENCES tenant(id);

UPDATE customer_account       SET tenant_id = 'tenant-default' WHERE tenant_id IS NULL;
UPDATE stablecoin_transaction SET tenant_id = 'tenant-default' WHERE tenant_id IS NULL;
UPDATE address_book           SET tenant_id = 'tenant-default' WHERE tenant_id IS NULL;
UPDATE yield_position         SET tenant_id = 'tenant-default' WHERE tenant_id IS NULL;
UPDATE audit_log              SET tenant_id = 'tenant-default' WHERE tenant_id IS NULL;

ALTER TABLE customer_account       ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE stablecoin_transaction ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE address_book           ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE yield_position         ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE audit_log              ALTER COLUMN tenant_id SET NOT NULL;

-- ============================================================
-- 4. Row-Level Security aktivieren
--    ENABLE (ohne FORCE): Table-Owner (stablecoin) bypassed automatisch.
--    stablecoin_app (Nicht-Owner) unterliegt vollständig den Policies.
-- ============================================================
ALTER TABLE customer_account       ENABLE ROW LEVEL SECURITY;
ALTER TABLE stablecoin_transaction ENABLE ROW LEVEL SECURITY;
ALTER TABLE address_book           ENABLE ROW LEVEL SECURITY;
ALTER TABLE yield_position         ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit_log              ENABLE ROW LEVEL SECURITY;

-- ============================================================
-- 5. Tenant-Isolation-Policies
--    current_setting('app.current_tenant', true): missing_ok=true ->
--    NULL statt Error wenn Variable nicht gesetzt ist.
-- ============================================================
CREATE POLICY tenant_isolation_policy ON customer_account
    USING (tenant_id = current_setting('app.current_tenant', true));

CREATE POLICY tenant_isolation_policy ON stablecoin_transaction
    USING (tenant_id = current_setting('app.current_tenant', true));

CREATE POLICY tenant_isolation_policy ON address_book
    USING (tenant_id = current_setting('app.current_tenant', true));

CREATE POLICY tenant_isolation_policy ON yield_position
    USING (tenant_id = current_setting('app.current_tenant', true));

CREATE POLICY tenant_isolation_policy ON audit_log
    USING (tenant_id = current_setting('app.current_tenant', true));
