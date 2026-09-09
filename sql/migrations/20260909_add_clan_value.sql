-- Giai đoạn 5B: materialize Clan Value để hồ sơ và xếp hạng giai đoạn 5C dùng chung.
-- Số dư quỹ không tham gia công thức; clan_achievement_score là nguồn điểm thành tựu độc lập.
ALTER TABLE clan ADD COLUMN clan_value BIGINT NOT NULL DEFAULT 0;
ALTER TABLE clan ADD COLUMN clan_value_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE clan ADD COLUMN clan_value_formula_version INT NOT NULL DEFAULT 0;
ALTER TABLE clan ADD COLUMN clan_achievement_score BIGINT NOT NULL DEFAULT 0;
ALTER TABLE clan ADD INDEX idx_clan_value (clan_value, id);
