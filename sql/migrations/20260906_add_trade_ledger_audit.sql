-- SEC-03: Additive migration for trade transaction ledger and audit reconciliation.
-- Idempotent schema definition; does not drop or rename existing tables or columns.
-- Note: Do NOT execute on production database without separate approval.

CREATE TABLE IF NOT EXISTS `trade_transaction_ledger` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `trade_id` VARCHAR(64) NOT NULL,
    `player_id_1` BIGINT NOT NULL,
    `player_id_2` BIGINT NOT NULL,
    `player_name_1` VARCHAR(64) NOT NULL,
    `player_name_2` VARCHAR(64) NOT NULL,
    `status` VARCHAR(32) NOT NULL,
    `result_code` VARCHAR(64) NOT NULL,
    `gold_trade_1` BIGINT NOT NULL DEFAULT 0,
    `gold_trade_2` BIGINT NOT NULL DEFAULT 0,
    `items_trade_1_json` MEDIUMTEXT NULL,
    `items_trade_2_json` MEDIUMTEXT NULL,
    `bag_1_before_json` MEDIUMTEXT NULL,
    `bag_2_before_json` MEDIUMTEXT NULL,
    `bag_1_after_json` MEDIUMTEXT NULL,
    `bag_2_after_json` MEDIUMTEXT NULL,
    `gold_1_before` BIGINT NOT NULL DEFAULT 0,
    `gold_2_before` BIGINT NOT NULL DEFAULT 0,
    `gold_1_after` BIGINT NOT NULL DEFAULT 0,
    `gold_2_after` BIGINT NOT NULL DEFAULT 0,
    `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_trade_ledger_trade_id` (`trade_id`),
    KEY `idx_trade_ledger_p1` (`player_id_1`, `created_at`),
    KEY `idx_trade_ledger_p2` (`player_id_2`, `created_at`),
    KEY `idx_trade_ledger_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
