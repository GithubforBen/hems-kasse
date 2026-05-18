ALTER TABLE sale ADD COLUMN transaction_ref VARCHAR(12) NOT NULL DEFAULT '';
UPDATE sale SET transaction_ref = UPPER(SUBSTRING(REPLACE(id::text, '-', ''), 1, 8));
ALTER TABLE sale ALTER COLUMN transaction_ref DROP DEFAULT;
