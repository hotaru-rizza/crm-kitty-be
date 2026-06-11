-- Deprecated: use Flyway migration src/main/resources/db/migration/V2__drop_removed_modules.sql

BEGIN;

-- Waiver module (code already removed)
DROP TABLE IF EXISTS signed_waivers CASCADE;
DROP TABLE IF EXISTS waiver_template_checkboxes CASCADE;
DROP TABLE IF EXISTS waiver_templates CASCADE;

ALTER TABLE IF EXISTS appointments DROP COLUMN IF EXISTS waiver_signed;

-- Inventory cluster
DROP TABLE IF EXISTS inventory_count_items CASCADE;
DROP TABLE IF EXISTS inventory_counts CASCADE;
DROP TABLE IF EXISTS supply_invoice_items CASCADE;
DROP TABLE IF EXISTS supply_invoices CASCADE;
DROP TABLE IF EXISTS stock_operations CASCADE;
DROP TABLE IF EXISTS products CASCADE;
DROP TABLE IF EXISTS warehouses CASCADE;

-- Gift certificates
DROP TABLE IF EXISTS gift_certificates CASCADE;

-- Promotions (backend module removed)
DROP TABLE IF EXISTS promotion_services CASCADE;
DROP TABLE IF EXISTS promotions CASCADE;

COMMIT;
