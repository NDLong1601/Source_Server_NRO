-- Lưu cấu hình ẩn hiệu ứng danh hiệu. Chỉ thêm cột khi database chưa có cột này.
SET @column_exists := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'player'
      AND column_name = 'hide_badges'
);

SET @sql := IF(
    @column_exists = 0,
    'ALTER TABLE `player` ADD COLUMN `hide_badges` TINYINT(1) NOT NULL DEFAULT 0 AFTER `dataBadges`',
    'SELECT 1'
);
PREPARE badge_visibility_statement FROM @sql;
EXECUTE badge_visibility_statement;
DEALLOCATE PREPARE badge_visibility_statement;
