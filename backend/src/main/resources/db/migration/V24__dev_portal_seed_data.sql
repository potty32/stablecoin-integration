-- V24: Dev-Portal Testdaten — alle Use-Cases ausführbar
-- Kunden für tenant-kleine-vb und tenant-grosse-vb,
-- Address-Book-Einträge (Whitelist) und Tenant-Konfiguration.
-- Idempotent via ON CONFLICT DO NOTHING / DO UPDATE.

-- ══════════════════════════════════════════════════════════════════
-- 1. CUSTOMER ACCOUNTS — tenant-kleine-vb
-- ══════════════════════════════════════════════════════════════════

INSERT INTO customer_account
    (id, customer_id, iban, wallet_address, customer_type, kyc_tier,
     tx_limit_single, tx_limit_daily, status, created_at, updated_at, tenant_id)
VALUES
    ('a0000001-0000-0000-0000-000000000001',
     'cust-b2b-001', 'DE89370400440532013010',
     '0xKleineVBB2BWallet00000000000000000000001',
     'B2B', 'TIER_3', 200000, 2000000, 'ACTIVE', NOW(), NOW(), 'tenant-kleine-vb'),
    ('a0000002-0000-0000-0000-000000000001',
     'cust-b2b-approver', 'DE89370400440532013011',
     '0xKleineVBApprover000000000000000000000001',
     'B2B', 'TIER_3', 200000, 2000000, 'ACTIVE', NOW(), NOW(), 'tenant-kleine-vb'),
    ('a0000003-0000-0000-0000-000000000001',
     'cust-b2c-001', 'DE27200400600532013010',
     '0xKleineVBB2CWallet00000000000000000000001',
     'B2C', 'TIER_2', 5000, 10000, 'ACTIVE', NOW(), NOW(), 'tenant-kleine-vb')
ON CONFLICT (id) DO NOTHING;

-- ══════════════════════════════════════════════════════════════════
-- 2. CUSTOMER ACCOUNTS — tenant-grosse-vb
-- ══════════════════════════════════════════════════════════════════

INSERT INTO customer_account
    (id, customer_id, iban, wallet_address, customer_type, kyc_tier,
     tx_limit_single, tx_limit_daily, status, created_at, updated_at, tenant_id)
VALUES
    ('b0000001-0000-0000-0000-000000000001',
     'cust-b2b-001', 'DE89370400440532013020',
     '0xGrosseVBB2BWallet00000000000000000000001',
     'B2B', 'TIER_3', 200000, 5000000, 'ACTIVE', NOW(), NOW(), 'tenant-grosse-vb'),
    ('b0000002-0000-0000-0000-000000000001',
     'cust-b2c-001', 'DE27200400600532013020',
     '0xGrosseVBB2CWallet00000000000000000000001',
     'B2C', 'TIER_2', 5000, 20000, 'ACTIVE', NOW(), NOW(), 'tenant-grosse-vb')
ON CONFLICT (id) DO NOTHING;

-- ══════════════════════════════════════════════════════════════════
-- 3. ADDRESS BOOK — Whitelist Wallets (tenant-kleine-vb)
-- MockChainalysis: 0xDEAD* → SANCTIONS, alle anderen → LOW_RISK
-- ══════════════════════════════════════════════════════════════════

INSERT INTO address_book
    (id, customer_account_id, label, wallet_address, currency, risk_score, status, verified_at, tenant_id)
VALUES
    ('c0000001-0000-0000-0000-000000000001',
     'a0000001-0000-0000-0000-000000000001',
     'Hauptpartner GmbH (USDC)',
     '0xRecipient0A10000000000000000000000001',
     'USDC', 'LOW', 'ACTIVE', NOW(), 'tenant-kleine-vb'),
    ('c0000002-0000-0000-0000-000000000001',
     'a0000001-0000-0000-0000-000000000001',
     'EU-Lieferant (EURC)',
     '0xEurcRecipientKlein000000000000000001',
     'EURC', 'LOW', 'ACTIVE', NOW(), 'tenant-kleine-vb'),
    ('c0000003-0000-0000-0000-000000000001',
     'a0000001-0000-0000-0000-000000000001',
     '⚠️ Sanktionierte Adresse (Compliance-Testfall)',
     '0xDEAD000000000000000000000000000000000000',
     'USDC', 'HIGH', 'ACTIVE', NOW(), 'tenant-kleine-vb')
ON CONFLICT (id) DO NOTHING;

-- ADDRESS BOOK — tenant-grosse-vb
INSERT INTO address_book
    (id, customer_account_id, label, wallet_address, currency, risk_score, status, verified_at, tenant_id)
VALUES
    ('d0000001-0000-0000-0000-000000000001',
     'b0000001-0000-0000-0000-000000000001',
     'Metropole Partner AG (USDC)',
     '0xRecipient0B20000000000000000000000001',
     'USDC', 'LOW', 'ACTIVE', NOW(), 'tenant-grosse-vb'),
    ('d0000002-0000-0000-0000-000000000001',
     'b0000001-0000-0000-0000-000000000001',
     'Interbanken-EURC Empfänger',
     '0xEurcRecipientGross000000000000000001',
     'EURC', 'LOW', 'ACTIVE', NOW(), 'tenant-grosse-vb')
ON CONFLICT (id) DO NOTHING;

-- ══════════════════════════════════════════════════════════════════
-- 4. TENANT SETTINGS
-- ══════════════════════════════════════════════════════════════════

INSERT INTO tenant_settings (
    tenant_id, fx_spread_b2b, fx_spread_b2c,
    fee_flat_b2b_eur, fee_flat_b2c_eur, remittance_fee_eur,
    approval_threshold_b2b, tx_limit_single_b2b, tx_limit_daily_b2b,
    tx_limit_single_b2c, tx_limit_daily_b2c,
    kill_switch_active, enable_yield, travel_rule_enabled,
    travel_rule_threshold_eur, updated_at)
VALUES
    ('tenant-kleine-vb',
     0.001500, 0.001500, 2.500000, 0.500000, 0.500000,
     25000, 500000, 2000000, 5000, 10000,
     FALSE, TRUE, FALSE, 15000, NOW()),
    ('tenant-grosse-vb',
     0.001200, 0.001200, 2.000000, 0.300000, 0.300000,
     50000, 500000, 5000000, 5000, 20000,
     FALSE, TRUE, FALSE, 15000, NOW())
ON CONFLICT (tenant_id) DO NOTHING;

-- ══════════════════════════════════════════════════════════════════
-- 5. SYSTEM_CONTROL — Kill-Switch Basis (falls nicht vorhanden)
-- ══════════════════════════════════════════════════════════════════

INSERT INTO system_control (scope, kill_switch_active)
VALUES ('GLOBAL', FALSE)
ON CONFLICT (scope) DO NOTHING;

-- ══════════════════════════════════════════════════════════════════
-- 6. INSTITUTIONAL ADDRESS BOOK — Interbanken-Adressen
-- ══════════════════════════════════════════════════════════════════

INSERT INTO institutional_address_book
    (id, label, wallet_address, currency, risk_score, status, verified_at, created_by)
VALUES
    ('e0000001-0000-0000-0000-000000000001',
     'Deutsche Bank USDC Custody',
     '0xDeutscheBankCustody0000000000000000001',
     'USDC', 'LOW', 'ACTIVE', NOW(), 'system'),
    ('e0000002-0000-0000-0000-000000000001',
     'DZ Bank EURC Settlement Wallet',
     '0xDZBankSettlement000000000000000000001',
     'EURC', 'LOW', 'ACTIVE', NOW(), 'system')
ON CONFLICT (id) DO NOTHING;
