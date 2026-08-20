-- V23: S-07 — Row-Level Security + tenant_id für Tabellen mit sensiblen Kundendaten
--
-- phone_alias: Telefonnummer-Hashes + Wallet-Adressen (DSGVO-kritisch)
--   → tenant_id hinzufügen (fehlt seit V1; Isolation bisher nur über customer_account FK)
--   → RLS aktivieren (identisches Muster wie V8)
--
-- approval_workflow + rate_quote:
--   → haben keine tenant_id-Spalte; Isolation über customer_account/transaction FK
--   → GRANT-Nachpflege falls fehlend

-- ── phone_alias: tenant_id Spalte + RLS ──────────────────────────────────────

ALTER TABLE phone_alias
    ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(50) REFERENCES tenant(id);

-- Backfill: tenant_id aus dem verknüpften customer_account ableiten
UPDATE phone_alias pa
    SET tenant_id = ca.tenant_id
    FROM customer_account ca
    WHERE pa.customer_account_id = ca.id
      AND pa.tenant_id IS NULL;

-- Fallback für verwaiste Einträge (sollte nicht vorkommen, aber defensiv)
UPDATE phone_alias
    SET tenant_id = 'tenant-default'
    WHERE tenant_id IS NULL;

ALTER TABLE phone_alias
    ALTER COLUMN tenant_id SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_phone_alias_tenant ON phone_alias(tenant_id);

ALTER TABLE phone_alias ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_policy ON phone_alias
    USING (tenant_id = current_setting('app.current_tenant', true));

GRANT SELECT, INSERT, UPDATE, DELETE ON phone_alias TO stablecoin_app;

-- ── approval_workflow: GRANT-Nachpflege ──────────────────────────────────────
DO $$ BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.role_table_grants
    WHERE grantee = 'stablecoin_app' AND table_name = 'approval_workflow' AND privilege_type = 'SELECT'
  ) THEN
    EXECUTE 'GRANT SELECT, INSERT, UPDATE ON approval_workflow TO stablecoin_app';
  END IF;
END $$;

-- ── rate_quote: GRANT-Nachpflege ──────────────────────────────────────────────
DO $$ BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.role_table_grants
    WHERE grantee = 'stablecoin_app' AND table_name = 'rate_quote' AND privilege_type = 'SELECT'
  ) THEN
    EXECUTE 'GRANT SELECT, INSERT, UPDATE ON rate_quote TO stablecoin_app';
  END IF;
END $$;
