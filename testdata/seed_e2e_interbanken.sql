-- E2E Testdaten: Mandantenübergreifender Stablecoin-Transfer (Interbanken-Clearance)
-- Atruvia Stablecoin Integration Platform · 2026-08-18
--
-- KORREKTURLISTE gegenüber der ursprünglichen Testspezifikation:
--   ✓ customer_name   → ENTFERNT (existiert nicht im Schema)
--   ✓ balance_eur     → ENTFERNT (existiert nicht im Schema)
--   ✓ kyc_status      → kyc_tier (korrekte Spalte)
--   ✓ TRUNCATE        → ON CONFLICT DO NOTHING (nicht destruktiv für parallele Tests)
--   ✓ Tenant-IDs nutzen bestehende Mandanten (tenant-kleine-vb, tenant-grosse-vb)
--   ✓ E2E-spezifische Wallet-Adressen (0x00...E2E1x, keine Konflikte mit Seed-Daten)
--   ✓ IBAN Mandant A: DE89370400440532013010 (neu, kein Konflikt)
--   ✓ IBAN Mandant B: DE89370400440532013002 (neu, kein Konflikt)
--
-- Verwendung: Wird vom Test CrossTenantInterbankenClearanceTest via adminJdbcTemplate
--             ausgeführt (@BeforeAll). Cleanup via @AfterAll.
--
-- Direkte Ausführung (manuell):
--   psql -U stablecoin -d stablecoin_dev -f testdata/seed_e2e_interbanken.sql

-- ============================================================
-- MANDANT A: Volksbank Kleinstadt eG (tenant-kleine-vb)
-- Konto: Müller GmbH (E2E-Initiator)
-- ============================================================
INSERT INTO customer_account (
    id, customer_id, iban, wallet_address,
    customer_type, kyc_tier,
    tx_limit_single, tx_limit_daily,
    status, created_at, updated_at, tenant_id
) VALUES (
    'e2e00001-0000-0000-0000-000000000001',
    'e2e-cust-b2b-001',
    'DE89370400440532013010',
    '0x00000000000000000000000000000000E2E1A001',
    'B2B', 'TIER_3',
    25000.000000, 2000000.000000,
    'ACTIVE', NOW(), NOW(),
    'tenant-kleine-vb'
)
ON CONFLICT (id) DO NOTHING;

-- ============================================================
-- MANDANT B: Volksbank Metropole eG (tenant-grosse-vb)
-- Konto: Schmidt AG (E2E-Empfänger via Inbound-Webhook)
-- ============================================================
INSERT INTO customer_account (
    id, customer_id, iban, wallet_address,
    customer_type, kyc_tier,
    tx_limit_single, tx_limit_daily,
    status, created_at, updated_at, tenant_id
) VALUES (
    'e2e00002-0000-0000-0000-000000000002',
    'e2e-cust-b2b-002',
    'DE89370400440532013002',
    '0x00000000000000000000000000000000E2E1B002',
    'B2B', 'TIER_3',
    50000.000000, 2000000.000000,
    'ACTIVE', NOW(), NOW(),
    'tenant-grosse-vb'
)
ON CONFLICT (id) DO NOTHING;

-- ============================================================
-- ADRESSBUCH: Mandant A whitelistet die Ziel-Wallet von Mandant B
-- Voraussetzung: Müller GmbH kann nur an pre-approved Wallets senden
-- ============================================================
INSERT INTO address_book (
    id, customer_account_id,
    label, wallet_address, currency,
    risk_score, status,
    verified_at, tenant_id
) VALUES (
    'e2eab00c-0000-0000-0000-000000000001',
    'e2e00001-0000-0000-0000-000000000001',
    'Schmidt AG - Gegenpartei Metropole (E2E)',
    '0x00000000000000000000000000000000E2E1B002',
    'USDC',
    'LOW', 'ACTIVE',
    NOW(),
    'tenant-kleine-vb'
)
ON CONFLICT (customer_account_id, wallet_address) DO NOTHING;

-- ============================================================
-- HINWEISE FÜR CLEANUP (@AfterAll):
--
-- DELETE FROM audit_log WHERE transaction_id IN (
--   SELECT id FROM stablecoin_transaction WHERE customer_account_id IN (
--     'e2e00001-0000-0000-0000-000000000001',
--     'e2e00002-0000-0000-0000-000000000002'
--   )
-- );
-- DELETE FROM outbox_message WHERE transaction_id IN (
--   SELECT id FROM stablecoin_transaction WHERE customer_account_id IN (...)
-- );
-- DELETE FROM stablecoin_transaction WHERE customer_account_id IN (...);
-- DELETE FROM address_book WHERE id = 'e2eab00c-0000-0000-0000-000000000001';
-- DELETE FROM customer_account WHERE id IN (
--   'e2e00001-0000-0000-0000-000000000001',
--   'e2e00002-0000-0000-0000-000000000002'
-- );
-- ============================================================
