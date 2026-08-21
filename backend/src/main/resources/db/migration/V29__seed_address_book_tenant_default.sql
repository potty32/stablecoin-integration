-- V29: Address-Book-Seed für tenant-default (cust-b2b-001)
-- Lokal: seed_dev.sql enthält diese Einträge, auf Railway fehlen sie.
-- Idempotent via ON CONFLICT DO NOTHING.

INSERT INTO address_book
    (id, customer_account_id, label, wallet_address, currency, risk_score, status, verified_at, tenant_id)
VALUES
    ('200cb9df-7573-4f0c-babf-76af663d9425',
     'a0000000-0000-0000-0000-000000000001',
     'Müller GmbH – USDC',
     '0xA100000000000000000000000000000000000001',
     'USDC', 'LOW', 'ACTIVE', NOW(), 'tenant-default'),
    ('9d7fc1b4-50a4-4da1-9bb0-fb280c8ae6da',
     'a0000000-0000-0000-0000-000000000001',
     'Schmidt AG – EURC',
     '0xA200000000000000000000000000000000000002',
     'EURC', 'LOW', 'ACTIVE', NOW(), 'tenant-default'),
    ('bcdd7572-78a9-457e-b01c-978af06a3a63',
     'a0000000-0000-0000-0000-000000000001',
     'Clearinghaus EU – USDC',
     '0xA500000000000000000000000000000000000005',
     'USDC', 'LOW', 'ACTIVE', NOW(), 'tenant-default'),
    ('39c28a4f-5500-4f0c-b001-000000000001',
     'a0000000-0000-0000-0000-000000000001',
     'Sanktionierte Adresse (Compliance-Test)',
     '0xDEAD000000000000000000000000000000000000',
     'USDC', 'HIGH', 'ACTIVE', NOW(), 'tenant-default')
ON CONFLICT (id) DO NOTHING;
