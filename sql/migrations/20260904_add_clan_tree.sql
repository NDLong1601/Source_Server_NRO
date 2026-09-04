-- Giai đoạn 2: trạng thái Cây bang chung và giới hạn chăm cây từng thành viên.
CREATE TABLE IF NOT EXISTS `clan_tree` (
  `clan_id` INT NOT NULL,
  `level` INT NOT NULL DEFAULT 1,
  `growth` BIGINT NOT NULL DEFAULT 0,
  `water` INT NOT NULL DEFAULT 0,
  `fertilizer` INT NOT NULL DEFAULT 0,
  `vitality_day` INT NOT NULL DEFAULT 0,
  `vitality_amount` INT NOT NULL DEFAULT 0,
  `pending_gold` BIGINT NOT NULL DEFAULT 0,
  `pending_capsule` INT NOT NULL DEFAULT 0,
  `last_production_at` BIGINT NOT NULL,
  `help_expires_at` BIGINT NOT NULL DEFAULT 0,
  `last_help_at` BIGINT NOT NULL DEFAULT 0,
  `upgrade_started_at` BIGINT NOT NULL DEFAULT 0,
  `upgrade_ready_at` BIGINT NOT NULL DEFAULT 0,
  `version` BIGINT NOT NULL DEFAULT 0,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`clan_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS `clan_tree_member` (
  `clan_id` INT NOT NULL,
  `player_id` BIGINT NOT NULL,
  `day_key` INT NOT NULL,
  `water_count` INT NOT NULL DEFAULT 0,
  `fertilizer_count` INT NOT NULL DEFAULT 0,
  `growth_contribution` BIGINT NOT NULL DEFAULT 0,
  `last_action_at` BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`clan_id`, `player_id`),
  KEY `idx_clan_tree_member_day` (`clan_id`, `day_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- Metadata cho 20 ảnh cây tĩnh (n_frame = 1). INSERT IGNORE giữ migration idempotent.
INSERT IGNORE INTO `img_by_name` (`NAME`, `n_frame`) VALUES
  ('cay_lv_01', 1), ('cay_lv_02', 1), ('cay_lv_03', 1), ('cay_lv_04', 1),
  ('cay_lv_05', 1), ('cay_lv_06', 1), ('cay_lv_07', 1), ('cay_lv_08', 1),
  ('cay_lv_09', 1), ('cay_lv_10', 1), ('cay_lv_11', 1), ('cay_lv_12', 1),
  ('cay_lv_13', 1), ('cay_lv_14', 1), ('cay_lv_15', 1), ('cay_lv_16', 1),
  ('cay_lv_17', 1), ('cay_lv_18', 1), ('cay_lv_19', 1), ('cay_lv_20', 1);
