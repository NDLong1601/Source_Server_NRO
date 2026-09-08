-- ==============================================================================
-- Migration: 20260907_add_vnd_ledger_and_outbox.sql
-- SEC-05: Durable VND Ledger and Recoverable Purchase Delivery
-- Idempotent, additive schema upgrade for vnd_transaction_ledger and vnd_delivery_outbox.
-- Preserves all existing columns, rows, account balances, and player data.
-- Do NOT execute against production database without separate explicit approval.
-- ==============================================================================

-- Preflight: atomicity requires both existing tables (account and player) to use InnoDB.
-- Abort the rollout if this check returns anything other than OK.
-- Execute in maintenance mode with all balance writers stopped. The temporary
-- NOT NULL assertion aborts batch execution before any permanent DDL.
SET SESSION sql_mode = CONCAT_WS(',', @@sql_mode, 'STRICT_ALL_TABLES');
CREATE TEMPORARY TABLE sec05_preflight_guard (ok INT NOT NULL);
INSERT INTO sec05_preflight_guard
SELECT IF(COUNT(*)=2 AND SUM(UPPER(ENGINE)='INNODB')=2,1,NULL)
FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN ('account','player');
INSERT INTO sec05_preflight_guard SELECT IF(COUNT(*)=0,1,NULL) FROM account WHERE vnd IS NULL OR vnd < 0;
INSERT INTO sec05_preflight_guard SELECT IF(COUNT(*)=0,1,NULL) FROM information_schema.TABLES
WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN ('vnd_transaction_ledger','vnd_delivery_outbox') AND UPPER(ENGINE)<>'INNODB';
SELECT IF(COUNT(*) = 2 AND SUM(UPPER(ENGINE) = 'INNODB') = 2,
          'OK', 'ABORT: account and player must both exist and use InnoDB') AS `sec05_engine_preflight`
  FROM information_schema.TABLES
 WHERE TABLE_SCHEMA = DATABASE()
   AND TABLE_NAME IN ('account', 'player');

-- 1. Create immutable vnd_transaction_ledger table
-- Holds permanent, append-only financial records for every debit, credit, or opening baseline.
CREATE TABLE IF NOT EXISTS `vnd_transaction_ledger` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `purchase_key` VARCHAR(128) NOT NULL,
    `account_id` INT NOT NULL,
    `player_id` BIGINT NOT NULL,
    `transaction_type` VARCHAR(32) NOT NULL COMMENT 'DEBIT_GOLD_CONVERT, DEBIT_GEM_CONVERT, DEBIT_VIP, CREDIT_ADMIN, BASELINE_OPENING',
    `amount` INT NOT NULL,
    `balance_before` INT NOT NULL,
    `balance_after` INT NOT NULL,
    `policy_version` VARCHAR(32) NOT NULL DEFAULT 'SEC-05-V1',
    `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_vnd_ledger_purchase_key` (`purchase_key`),
    KEY `idx_vnd_ledger_account` (`account_id`, `created_at`),
    KEY `idx_vnd_ledger_player` (`player_id`, `created_at`),
    KEY `idx_vnd_ledger_type` (`transaction_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- 2. Create durable vnd_delivery_outbox table
-- Manages delivery state, frozen complete entitlement payloads, retry metadata, and error codes.
CREATE TABLE IF NOT EXISTS `vnd_delivery_outbox` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `purchase_key` VARCHAR(128) NOT NULL,
    `account_id` INT NOT NULL,
    `player_id` BIGINT NOT NULL,
    `product_type` VARCHAR(32) NOT NULL COMMENT 'TRADE_GOLD, TRADE_GEM, VIP_1, VIP_2, VIP_3, VIP_4',
    `amount` INT NOT NULL,
    `payload_fingerprint` VARCHAR(64) NOT NULL,
    `frozen_entitlement_json` MEDIUMTEXT NOT NULL,
    `entitlement_version` INT NOT NULL DEFAULT 1,
    `status` VARCHAR(32) NOT NULL DEFAULT 'PENDING_DELIVERY' COMMENT 'PENDING_DELIVERY, DELIVERED, FAILED_RETRYABLE, QUARANTINED',
    `error_code` VARCHAR(64) NULL DEFAULT NULL,
    `retry_count` INT NOT NULL DEFAULT 0,
    `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `delivered_at` TIMESTAMP NULL DEFAULT NULL,
    `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_vnd_outbox_purchase_key` (`purchase_key`),
    KEY `idx_vnd_outbox_status` (`status`),
    KEY `idx_vnd_outbox_player_status` (`player_id`, `status`),
    KEY `idx_vnd_outbox_account` (`account_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- SEC-07 owns baseline capture. This older SEC-05 migration deliberately does
-- not infer an opening value from a current balance; run
-- sql/operations/sec07_capture_vnd_baseline.sql at an approved cutover.
DROP TEMPORARY TABLE sec05_preflight_guard;
