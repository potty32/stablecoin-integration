-- V27: V24-Duplikate bei customer_id beheben
-- V24 hat cust-b2b-001 in mehreren Tenants angelegt. Ohne RLS-Enforcement
-- (z.B. auf Railway, wo der DB-Owner RLS bypassed) liefert findByCustomerId()
-- mehrere Ergebnisse → NonUniqueResultException.
-- Lösung: customer_id pro Tenant eindeutig machen.

UPDATE customer_account
SET customer_id = 'cust-kleine-vb-b2b-001'
WHERE customer_id = 'cust-b2b-001'
  AND tenant_id   = 'tenant-kleine-vb';

UPDATE customer_account
SET customer_id = 'cust-grosse-vb-b2b-001'
WHERE customer_id = 'cust-b2b-001'
  AND tenant_id   = 'tenant-grosse-vb';

UPDATE customer_account
SET customer_id = 'cust-kleine-vb-b2c-001'
WHERE customer_id = 'cust-b2c-001'
  AND tenant_id   = 'tenant-kleine-vb';

UPDATE customer_account
SET customer_id = 'cust-grosse-vb-b2c-001'
WHERE customer_id = 'cust-b2c-001'
  AND tenant_id   = 'tenant-grosse-vb';
