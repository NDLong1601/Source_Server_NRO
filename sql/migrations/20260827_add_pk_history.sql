CREATE TABLE IF NOT EXISTS `pk_history` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `winner_id` BIGINT NOT NULL,
    `winner_name` VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
    `loser_id` BIGINT NOT NULL,
    `loser_name` VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
    `result_type` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0: kiệt sức, 1: bỏ chạy',
    `wager_gold` INT UNSIGNED NOT NULL DEFAULT 0,
    `map_id` SMALLINT NOT NULL DEFAULT -1,
    `zone_id` SMALLINT NOT NULL DEFAULT -1,
    `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_pk_history_winner_time` (`winner_id`, `created_at`),
    KEY `idx_pk_history_loser_time` (`loser_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
