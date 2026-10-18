-- Run once on existing PostgreSQL databases before starting the updated backend.
-- Fresh databases already use text through the JPA mappings.
BEGIN;
ALTER TABLE component_ingredient
    ALTER COLUMN inventory_item_id TYPE text USING inventory_item_id::text;
ALTER TABLE slot_option
    ALTER COLUMN inventory_item_id TYPE text USING inventory_item_id::text;
COMMIT;
