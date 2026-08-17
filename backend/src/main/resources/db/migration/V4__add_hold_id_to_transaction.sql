-- hold_id: speichert die Core-Banking-Hold-ID für automatischen Release bei FAILED
ALTER TABLE stablecoin_transaction ADD COLUMN IF NOT EXISTS hold_id VARCHAR(100);
