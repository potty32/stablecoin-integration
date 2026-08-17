-- 1. Alten Constraint entfernen (erlaubt danach alle Werte temporär)
ALTER TABLE stablecoin_transaction
    DROP CONSTRAINT IF EXISTS stablecoin_transaction_status_check;

-- 2. Historische Datensätze migrieren (old enum → new enum)
--    Muss NACH dem DROP laufen, da neue Werte den alten Constraint verletzen würden
UPDATE stablecoin_transaction SET status = 'CREATED'            WHERE status = 'PENDING';
UPDATE stablecoin_transaction SET status = 'PENDING_APPROVAL'   WHERE status = 'AWAITING_APPROVAL';
UPDATE stablecoin_transaction SET status = 'COMPLIANCE_CHECKED' WHERE status = 'COMPLIANCE_CHECK';
UPDATE stablecoin_transaction SET status = 'SUBMITTED'          WHERE status = 'PROCESSING';
UPDATE stablecoin_transaction SET status = 'FAILED'             WHERE status = 'BLOCKED';

-- 3. Neuen Constraint mit allen 11 Werten anlegen
ALTER TABLE stablecoin_transaction
    ADD CONSTRAINT stablecoin_transaction_status_check
    CHECK (status IN (
        'CREATED',
        'PENDING_APPROVAL',
        'APPROVED',
        'REJECTED',
        'EXPIRED',
        'COMPLIANCE_CHECKED',
        'FUNDS_HELD',
        'SUBMITTED',
        'SETTLED',
        'REDEEMED',
        'FAILED'
    ));

-- 4. Default-Wert von PENDING auf CREATED aktualisieren
ALTER TABLE stablecoin_transaction
    ALTER COLUMN status SET DEFAULT 'CREATED';
