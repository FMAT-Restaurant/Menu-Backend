-- Run once before starting the updated backend against an existing PostgreSQL database.
-- JPA creates the column and the table automatically for fresh databases.
-- Inventory options start not available until Inventory communicates their availability (BE-10).
BEGIN;
ALTER TABLE slot_option ADD COLUMN IF NOT EXISTS source_available boolean NOT NULL DEFAULT false;
ALTER TABLE recipe_revision ALTER COLUMN is_available SET DEFAULT false;
CREATE TABLE IF NOT EXISTS processed_event (
    event_id text PRIMARY KEY,
    processed_at timestamp(6) with time zone NOT NULL
);
COMMIT;
