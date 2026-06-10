-- Allow multiple cashiers from the same class to share one shift:
-- subjectKey format changes from "verkauf:klasse:name" → "verkauf:klasse"
UPDATE shift
SET subject_key = 'verkauf:' || split_part(subject_key, ':', 2)
WHERE subject_key ~ '^verkauf:[^:]+:[^:]+$';

-- Variable-price products: price is entered by the cashier at point of sale
ALTER TABLE product ADD COLUMN IF NOT EXISTS variable BOOLEAN NOT NULL DEFAULT FALSE;
