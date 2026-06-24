ALTER TABLE transactions DROP CONSTRAINT IF EXISTS transactions_category_check;

UPDATE transactions SET category = LOWER(category);
