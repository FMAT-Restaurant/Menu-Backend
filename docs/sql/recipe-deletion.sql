-- Run once before starting the updated backend against an existing PostgreSQL database.
-- JPA creates the column automatically for fresh databases.
-- Recipe rows are retained to preserve the published revisions and their ingredients.
BEGIN;
ALTER TABLE recipe ADD COLUMN IF NOT EXISTS deleted boolean NOT NULL DEFAULT false;
COMMIT;
