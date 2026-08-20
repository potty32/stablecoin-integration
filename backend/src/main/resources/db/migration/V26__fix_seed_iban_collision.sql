-- V26: IBANs der V24-Seed-Accounts auf kollisionsfreie Werte aktualisieren
-- V24 verwendete 013010/013011/013020 — diese überschneiden sich mit den
-- E2E-Testkonten (CrossTenantInterbankenClearanceTest: 013010, 013002).
-- Neue IBANs im Bereich 090xxx — klar erkennbar als "Dev-Portal-Demo-Konten".

UPDATE customer_account
  SET iban = 'DE89370400440532090001'
  WHERE id = 'a0000001-0000-0000-0000-000000000001';  -- cust-b2b-001 / tenant-kleine-vb

UPDATE customer_account
  SET iban = 'DE89370400440532090002'
  WHERE id = 'a0000002-0000-0000-0000-000000000001';  -- cust-b2b-approver / tenant-kleine-vb

UPDATE customer_account
  SET iban = 'DE27200400600532090001'
  WHERE id = 'a0000003-0000-0000-0000-000000000001';  -- cust-b2c-001 / tenant-kleine-vb

UPDATE customer_account
  SET iban = 'DE89370400440532090003'
  WHERE id = 'b0000001-0000-0000-0000-000000000001';  -- cust-b2b-001 / tenant-grosse-vb

UPDATE customer_account
  SET iban = 'DE27200400600532090003'
  WHERE id = 'b0000002-0000-0000-0000-000000000001';  -- cust-b2c-001 / tenant-grosse-vb
