-- V28: V27-Renames rückgängig machen
-- V27 hatte customer_ids umbenannt um NonUniqueResultException zu vermeiden.
-- Fix: tenant-aware Queries (findByCustomerIdAndTenantId) in den Services.
-- Die Daten werden wiederhergestellt damit JWT-Sub cust-b2b-001 in allen Tenants funktioniert.

UPDATE customer_account
SET customer_id = 'cust-b2b-001'
WHERE customer_id = 'cust-kleine-vb-b2b-001'
  AND tenant_id   = 'tenant-kleine-vb';

UPDATE customer_account
SET customer_id = 'cust-b2b-001'
WHERE customer_id = 'cust-grosse-vb-b2b-001'
  AND tenant_id   = 'tenant-grosse-vb';

UPDATE customer_account
SET customer_id = 'cust-b2c-001'
WHERE customer_id = 'cust-kleine-vb-b2c-001'
  AND tenant_id   = 'tenant-kleine-vb';

UPDATE customer_account
SET customer_id = 'cust-b2c-001'
WHERE customer_id = 'cust-grosse-vb-b2c-001'
  AND tenant_id   = 'tenant-grosse-vb';
