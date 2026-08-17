-- 1. Neue Spalten hinzufügen
ALTER TABLE audit_log ADD COLUMN transaction_id UUID REFERENCES stablecoin_transaction(id);
ALTER TABLE audit_log ADD COLUMN from_status VARCHAR(25);
ALTER TABLE audit_log ADD COLUMN to_status VARCHAR(25);
ALTER TABLE audit_log ADD COLUMN details TEXT;

-- 2. TX-Einträge: transaction_id befüllen
UPDATE audit_log
SET transaction_id = entity_id
WHERE entity_type = 'StablecoinTransaction';

-- 3. from_status aus previous_state (mit Mapping alter → neuer Enum-Werte)
UPDATE audit_log
SET from_status = CASE (previous_state->>'status')
    WHEN 'PENDING'            THEN 'CREATED'
    WHEN 'AWAITING_APPROVAL'  THEN 'PENDING_APPROVAL'
    WHEN 'COMPLIANCE_CHECK'   THEN 'COMPLIANCE_CHECKED'
    WHEN 'PROCESSING'         THEN 'SUBMITTED'
    WHEN 'BLOCKED'            THEN 'FAILED'
    ELSE previous_state->>'status'
END
WHERE entity_type = 'StablecoinTransaction'
  AND previous_state IS NOT NULL
  AND previous_state->>'status' IS NOT NULL;

-- 4. to_status aus new_state
UPDATE audit_log
SET to_status = CASE (new_state->>'status')
    WHEN 'PENDING'            THEN 'CREATED'
    WHEN 'AWAITING_APPROVAL'  THEN 'PENDING_APPROVAL'
    WHEN 'COMPLIANCE_CHECK'   THEN 'COMPLIANCE_CHECKED'
    WHEN 'PROCESSING'         THEN 'SUBMITTED'
    WHEN 'BLOCKED'            THEN 'FAILED'
    ELSE new_state->>'status'
END
WHERE entity_type = 'StablecoinTransaction'
  AND new_state IS NOT NULL
  AND new_state->>'status' IS NOT NULL;

-- 5. details Klartext befüllen
UPDATE audit_log
SET details = CASE action
    WHEN 'CREATED' THEN
        'Transaktion erstellt, Betrag: ' || COALESCE(new_state->>'amount', '?') || ' EUR'
    WHEN 'TRANSITION' THEN
        COALESCE(previous_state->>'status', 'INIT') || ' → ' || COALESCE(new_state->>'status', '?')
    WHEN 'COMPLIANCE_SCREEN' THEN
        'AML-Screening: riskScore=' || COALESCE(new_state->>'riskScore', '?') || ', approved=' || COALESCE(new_state->>'approved', '?')
    WHEN 'COMPLIANCE_BLOCKED' THEN
        'AML-Block: riskScore=' || COALESCE(new_state->>'riskScore', '?')
    WHEN 'COMPLIANCE_FALLBACK_BLOCK' THEN
        'AML-Block (Chainalysis nicht erreichbar)'
    ELSE NULL
END
WHERE details IS NULL;

-- 6. Index für schnelle Timeline-Abfragen
CREATE INDEX ON audit_log (transaction_id) WHERE transaction_id IS NOT NULL;

-- 7. Alte JSONB-Spalten entfernen
ALTER TABLE audit_log DROP COLUMN IF EXISTS previous_state;
ALTER TABLE audit_log DROP COLUMN IF EXISTS new_state;
ALTER TABLE audit_log DROP COLUMN IF EXISTS ip_address;
ALTER TABLE audit_log DROP COLUMN IF EXISTS trace_id;
