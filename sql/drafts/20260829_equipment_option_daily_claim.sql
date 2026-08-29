-- Option 161: mỗi nhân vật chỉ nhận ngọc từ lần hạ quái đầu tiên trong ngày.
CREATE TABLE IF NOT EXISTS `equipment_option_daily_claim` (
  `player_id` bigint NOT NULL,
  `claim_date` date NOT NULL,
  PRIMARY KEY (`player_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
