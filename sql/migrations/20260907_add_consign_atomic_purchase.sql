-- ==============================================================================
-- Migration: 20260907_add_consign_atomic_purchase.sql
-- SEC-04: Consignment Shop Atomic Purchase and Safe Listing Lifecycle
-- Idempotent, additive schema upgrade for shop_ky_gui and consign_purchase_ledger.
-- Preserves all existing columns, rows, and historical data.
-- Do NOT execute against production database without separate explicit approval.
-- ==============================================================================

-- Preflight: atomicity requires both existing tables to use InnoDB. Abort the
-- rollout if this query returns anything other than OK. The repository schema
-- dump already defines both tables as InnoDB; this protects drifted databases.
SELECT IF(COUNT(*) = 2 AND SUM(UPPER(ENGINE) = 'INNODB') = 2,
          'OK', 'ABORT: player and shop_ky_gui must both exist and use InnoDB') AS `sec04_engine_preflight`
  FROM information_schema.TABLES
 WHERE TABLE_SCHEMA = DATABASE()
   AND TABLE_NAME IN ('player', 'shop_ky_gui');

-- 1. Enhance shop_ky_gui table with lifecycle status, optimistic locking version,
--    buyer audit reference, and transition timestamps.
--    (Note: MariaDB/MySQL 8 support ADD COLUMN IF NOT EXISTS; standard syntax used below).

-- Check and add columns safely if they do not exist
SET @col_status_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shop_ky_gui' AND COLUMN_NAME = 'status');
SET @sql_status := IF(@col_status_exists = 0, 'ALTER TABLE `shop_ky_gui` ADD COLUMN `status` VARCHAR(20) NOT NULL DEFAULT \'ACTIVE\' AFTER `isBuy`', 'SELECT 1');
PREPARE stmt FROM @sql_status;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_buyer_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shop_ky_gui' AND COLUMN_NAME = 'buyer_id');
SET @sql_buyer := IF(@col_buyer_exists = 0, 'ALTER TABLE `shop_ky_gui` ADD COLUMN `buyer_id` BIGINT NULL DEFAULT NULL AFTER `status`', 'SELECT 1');
PREPARE stmt FROM @sql_buyer;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_ver_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shop_ky_gui' AND COLUMN_NAME = 'version');
SET @sql_ver := IF(@col_ver_exists = 0, 'ALTER TABLE `shop_ky_gui` ADD COLUMN `version` INT NOT NULL DEFAULT 1 AFTER `buyer_id`', 'SELECT 1');
PREPARE stmt FROM @sql_ver;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_created_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shop_ky_gui' AND COLUMN_NAME = 'created_at');
SET @sql_created := IF(@col_created_exists = 0, 'ALTER TABLE `shop_ky_gui` ADD COLUMN `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP AFTER `version`', 'SELECT 1');
PREPARE stmt FROM @sql_created;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_sold_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shop_ky_gui' AND COLUMN_NAME = 'sold_at');
SET @sql_sold := IF(@col_sold_exists = 0, 'ALTER TABLE `shop_ky_gui` ADD COLUMN `sold_at` TIMESTAMP NULL DEFAULT NULL AFTER `created_at`', 'SELECT 1');
PREPARE stmt FROM @sql_sold;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_updated_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shop_ky_gui' AND COLUMN_NAME = 'updated_at');
SET @sql_updated := IF(@col_updated_exists = 0, 'ALTER TABLE `shop_ky_gui` ADD COLUMN `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP AFTER `sold_at`', 'SELECT 1');
PREPARE stmt FROM @sql_updated;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Normalize a previously deployed numeric status column before backfilling.
SET @status_data_type := (SELECT DATA_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shop_ky_gui' AND COLUMN_NAME = 'status' LIMIT 1);
SET @sql_status_type := IF(LOWER(@status_data_type) <> 'varchar', 'ALTER TABLE `shop_ky_gui` MODIFY COLUMN `status` VARCHAR(20) NOT NULL DEFAULT ''ACTIVE''', 'SELECT 1');
PREPARE stmt FROM @sql_status_type;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2. Backfill only legacy rows. This is deliberately idempotent: rerunning the
--    migration must never turn CANCELLED/CLAIMED listings back into ACTIVE.
UPDATE `shop_ky_gui`
   SET `status` = CASE `status`
       WHEN '0' THEN 'ACTIVE'
       WHEN '1' THEN 'SOLD'
       WHEN '2' THEN 'CLAIMED'
       WHEN '3' THEN 'CANCELLED'
       ELSE UPPER(`status`)
   END
 WHERE `status` IN ('0', '1', '2', '3')
    OR `status` <> UPPER(`status`);

UPDATE `shop_ky_gui`
   SET `status` = 'CANCELLED'
 WHERE `status` IS NULL
    OR TRIM(`status`) = ''
    OR `status` NOT IN ('ACTIVE', 'SOLD', 'CLAIMED', 'CANCELLED');

UPDATE `shop_ky_gui` SET `status` = 'SOLD' WHERE `isBuy` = 1 AND `status` = 'ACTIVE';
UPDATE `shop_ky_gui` SET `version` = 1 WHERE `version` IS NULL OR `version` < 1;

-- 3. Add secondary indexes for efficient lookup and state transitions
SET @idx_status_exists := (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shop_ky_gui' AND INDEX_NAME = 'idx_shop_ky_gui_status');
SET @sql_idx_status := IF(@idx_status_exists = 0, 'ALTER TABLE `shop_ky_gui` ADD INDEX `idx_shop_ky_gui_status` (`status`)', 'SELECT 1');
PREPARE stmt FROM @sql_idx_status;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_seller_exists := (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shop_ky_gui' AND INDEX_NAME = 'idx_shop_ky_gui_seller');
SET @sql_idx_seller := IF(@idx_seller_exists = 0, 'ALTER TABLE `shop_ky_gui` ADD INDEX `idx_shop_ky_gui_seller` (`player_id`, `status`)', 'SELECT 1');
PREPARE stmt FROM @sql_idx_seller;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_tab_status_exists := (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shop_ky_gui' AND INDEX_NAME = 'idx_shop_ky_gui_tab_status');
SET @sql_idx_tab_status := IF(@idx_tab_status_exists = 0, 'ALTER TABLE `shop_ky_gui` ADD INDEX `idx_shop_ky_gui_tab_status` (`tab`, `status`)', 'SELECT 1');
PREPARE stmt FROM @sql_idx_tab_status;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 4. Create consign_purchase_ledger table for immutable purchase records & audit outbox
CREATE TABLE IF NOT EXISTS `consign_purchase_ledger` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `listing_id` INT NOT NULL,
    `seller_id` BIGINT NOT NULL,
    `buyer_id` BIGINT NOT NULL,
    `price_type` TINYINT NOT NULL COMMENT '0=gold, 1=gem',
    `price` INT NOT NULL,
    `item_id` INT NOT NULL,
    `quantity` INT NOT NULL,
    `item_options_json` MEDIUMTEXT NOT NULL,
    `status` VARCHAR(32) NOT NULL DEFAULT 'SUCCESS',
    `buyer_gold_before` BIGINT NOT NULL DEFAULT 0,
    `buyer_gold_after` BIGINT NOT NULL DEFAULT 0,
    `buyer_gem_before` INT NOT NULL DEFAULT 0,
    `buyer_gem_after` INT NOT NULL DEFAULT 0,
    `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_consign_purchase_listing` (`listing_id`),
    KEY `idx_consign_purchase_buyer` (`buyer_id`, `created_at`),
    KEY `idx_consign_purchase_seller` (`seller_id`, `created_at`),
    KEY `idx_consign_purchase_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- Upgrade the earlier SEC-04 draft ledger shape (tab/cost/buy_type) in place.
-- Columns are introduced nullable first so historical rows can be backfilled.
SET @ledger_price_type_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'consign_purchase_ledger' AND COLUMN_NAME = 'price_type');
SET @sql_ledger_price_type := IF(@ledger_price_type_exists = 0, 'ALTER TABLE `consign_purchase_ledger` ADD COLUMN `price_type` TINYINT NULL DEFAULT NULL AFTER `buyer_id`', 'SELECT 1');
PREPARE stmt FROM @sql_ledger_price_type;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ledger_price_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'consign_purchase_ledger' AND COLUMN_NAME = 'price');
SET @sql_ledger_price := IF(@ledger_price_exists = 0, 'ALTER TABLE `consign_purchase_ledger` ADD COLUMN `price` INT NULL DEFAULT NULL AFTER `price_type`', 'SELECT 1');
PREPARE stmt FROM @sql_ledger_price;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ledger_options_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'consign_purchase_ledger' AND COLUMN_NAME = 'item_options_json');
SET @sql_ledger_options := IF(@ledger_options_exists = 0, 'ALTER TABLE `consign_purchase_ledger` ADD COLUMN `item_options_json` MEDIUMTEXT NULL AFTER `quantity`', 'SELECT 1');
PREPARE stmt FROM @sql_ledger_options;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ledger_gold_before_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'consign_purchase_ledger' AND COLUMN_NAME = 'buyer_gold_before');
SET @sql_ledger_gold_before := IF(@ledger_gold_before_exists = 0, 'ALTER TABLE `consign_purchase_ledger` ADD COLUMN `buyer_gold_before` BIGINT NOT NULL DEFAULT 0', 'SELECT 1');
PREPARE stmt FROM @sql_ledger_gold_before;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ledger_gold_after_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'consign_purchase_ledger' AND COLUMN_NAME = 'buyer_gold_after');
SET @sql_ledger_gold_after := IF(@ledger_gold_after_exists = 0, 'ALTER TABLE `consign_purchase_ledger` ADD COLUMN `buyer_gold_after` BIGINT NOT NULL DEFAULT 0', 'SELECT 1');
PREPARE stmt FROM @sql_ledger_gold_after;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ledger_gem_before_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'consign_purchase_ledger' AND COLUMN_NAME = 'buyer_gem_before');
SET @sql_ledger_gem_before := IF(@ledger_gem_before_exists = 0, 'ALTER TABLE `consign_purchase_ledger` ADD COLUMN `buyer_gem_before` INT NOT NULL DEFAULT 0', 'SELECT 1');
PREPARE stmt FROM @sql_ledger_gem_before;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ledger_gem_after_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'consign_purchase_ledger' AND COLUMN_NAME = 'buyer_gem_after');
SET @sql_ledger_gem_after := IF(@ledger_gem_after_exists = 0, 'ALTER TABLE `consign_purchase_ledger` ADD COLUMN `buyer_gem_after` INT NOT NULL DEFAULT 0', 'SELECT 1');
PREPARE stmt FROM @sql_ledger_gem_after;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ledger_updated_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'consign_purchase_ledger' AND COLUMN_NAME = 'updated_at');
SET @sql_ledger_updated := IF(@ledger_updated_exists = 0, 'ALTER TABLE `consign_purchase_ledger` ADD COLUMN `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP', 'SELECT 1');
PREPARE stmt FROM @sql_ledger_updated;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @legacy_buy_type_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'consign_purchase_ledger' AND COLUMN_NAME = 'buy_type');
SET @sql_backfill_price_type := IF(@legacy_buy_type_exists = 1, 'UPDATE `consign_purchase_ledger` SET `price_type` = `buy_type` WHERE `price_type` IS NULL', 'SELECT 1');
PREPARE stmt FROM @sql_backfill_price_type;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @legacy_cost_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'consign_purchase_ledger' AND COLUMN_NAME = 'cost');
SET @sql_backfill_price := IF(@legacy_cost_exists = 1, 'UPDATE `consign_purchase_ledger` SET `price` = `cost` WHERE `price` IS NULL', 'SELECT 1');
PREPARE stmt FROM @sql_backfill_price;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE `consign_purchase_ledger` ledger
LEFT JOIN `shop_ky_gui` listing ON listing.`id` = ledger.`listing_id`
   SET ledger.`item_options_json` = COALESCE(listing.`itemOption`, '[]')
 WHERE ledger.`item_options_json` IS NULL OR TRIM(ledger.`item_options_json`) = '';

-- New writes use the canonical columns. Retained draft columns become optional
-- so they cannot reject inserts that intentionally omit duplicate data.
SET @legacy_tab_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'consign_purchase_ledger' AND COLUMN_NAME = 'tab');
SET @sql_legacy_tab := IF(@legacy_tab_exists = 1, 'ALTER TABLE `consign_purchase_ledger` MODIFY COLUMN `tab` TINYINT NULL DEFAULT NULL', 'SELECT 1');
PREPARE stmt FROM @sql_legacy_tab;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql_legacy_cost := IF(@legacy_cost_exists = 1, 'ALTER TABLE `consign_purchase_ledger` MODIFY COLUMN `cost` INT NULL DEFAULT NULL', 'SELECT 1');
PREPARE stmt FROM @sql_legacy_cost;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql_legacy_buy_type := IF(@legacy_buy_type_exists = 1, 'ALTER TABLE `consign_purchase_ledger` MODIFY COLUMN `buy_type` TINYINT NULL DEFAULT NULL', 'SELECT 1');
PREPARE stmt FROM @sql_legacy_buy_type;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE `consign_purchase_ledger`
    MODIFY COLUMN `price_type` TINYINT NOT NULL,
    MODIFY COLUMN `price` INT NOT NULL,
    MODIFY COLUMN `item_options_json` MEDIUMTEXT NOT NULL;

SET @ledger_unique_exists := (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'consign_purchase_ledger' AND INDEX_NAME = 'uk_consign_purchase_listing');
SET @sql_ledger_unique := IF(@ledger_unique_exists = 0, 'ALTER TABLE `consign_purchase_ledger` ADD UNIQUE INDEX `uk_consign_purchase_listing` (`listing_id`)', 'SELECT 1');
PREPARE stmt FROM @sql_ledger_unique;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ledger_buyer_index_exists := (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'consign_purchase_ledger' AND INDEX_NAME = 'idx_consign_purchase_buyer');
SET @sql_ledger_buyer_index := IF(@ledger_buyer_index_exists = 0, 'ALTER TABLE `consign_purchase_ledger` ADD INDEX `idx_consign_purchase_buyer` (`buyer_id`, `created_at`)', 'SELECT 1');
PREPARE stmt FROM @sql_ledger_buyer_index;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ledger_seller_index_exists := (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'consign_purchase_ledger' AND INDEX_NAME = 'idx_consign_purchase_seller');
SET @sql_ledger_seller_index := IF(@ledger_seller_index_exists = 0, 'ALTER TABLE `consign_purchase_ledger` ADD INDEX `idx_consign_purchase_seller` (`seller_id`, `created_at`)', 'SELECT 1');
PREPARE stmt FROM @sql_ledger_seller_index;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ledger_status_index_exists := (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'consign_purchase_ledger' AND INDEX_NAME = 'idx_consign_purchase_status');
SET @sql_ledger_status_index := IF(@ledger_status_index_exists = 0, 'ALTER TABLE `consign_purchase_ledger` ADD INDEX `idx_consign_purchase_status` (`status`)', 'SELECT 1');
PREPARE stmt FROM @sql_ledger_status_index;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 5. Persistent monotonic allocator. IDs are written as signed shorts on the
-- wire, so exhaustion fails closed instead of recycling a historical ID (ABA).
CREATE TABLE IF NOT EXISTS `consign_listing_sequence` (
    `singleton_id` TINYINT NOT NULL,
    `next_id` INT NOT NULL,
    PRIMARY KEY (`singleton_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

SET @next_consign_id := (
    SELECT LEAST(GREATEST(COALESCE(MAX(`id`), 0) + 1, 1), 32768)
      FROM `shop_ky_gui`
);
INSERT INTO `consign_listing_sequence` (`singleton_id`, `next_id`)
VALUES (1, @next_consign_id)
ON DUPLICATE KEY UPDATE `next_id` = GREATEST(`next_id`, @next_consign_id);

-- ==============================================================================
-- Verification Queries (Run after applying migration to confirm schema integrity):
--
-- 1. Check shop_ky_gui columns:
--    SELECT column_name, data_type, is_nullable, column_default
--      FROM information_schema.columns
--     WHERE table_schema = DATABASE() AND table_name = 'shop_ky_gui';
--
-- 2. Check shop_ky_gui status distribution:
--    SELECT status, isBuy, COUNT(*)
--      FROM shop_ky_gui
--     GROUP BY status, isBuy;
--
-- 3. Check consign_purchase_ledger structure:
--    DESCRIBE consign_purchase_ledger;
--
-- 4. Check the non-recycling ID sequence:
--    SELECT singleton_id, next_id FROM consign_listing_sequence;
-- ==============================================================================

-- ==============================================================================
-- Rollback Guidance (If reversal is ever required in staging/development):
--
-- Note: Do NOT execute rollback on production if live trades have already occurred.
--
-- DROP TABLE IF EXISTS `consign_listing_sequence`;
-- DROP TABLE IF EXISTS `consign_purchase_ledger`;
-- ALTER TABLE `shop_ky_gui` DROP INDEX `idx_shop_ky_gui_tab_status`;
-- ALTER TABLE `shop_ky_gui` DROP INDEX `idx_shop_ky_gui_seller`;
-- ALTER TABLE `shop_ky_gui` DROP INDEX `idx_shop_ky_gui_status`;
-- ALTER TABLE `shop_ky_gui` DROP COLUMN `updated_at`;
-- ALTER TABLE `shop_ky_gui` DROP COLUMN `sold_at`;
-- ALTER TABLE `shop_ky_gui` DROP COLUMN `created_at`;
-- ALTER TABLE `shop_ky_gui` DROP COLUMN `version`;
-- ALTER TABLE `shop_ky_gui` DROP COLUMN `buyer_id`;
-- ALTER TABLE `shop_ky_gui` DROP COLUMN `status`;
-- ==============================================================================
