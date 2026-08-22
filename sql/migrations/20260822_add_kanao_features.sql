-- Adds Kanao's two empty shop tabs. Quest progress is stored in the existing
-- player.nhiem_vu_kol JSON column, so no player schema change is required.
CREATE TABLE IF NOT EXISTS `admin_npc_render_config` (
    `npc_id` INT NOT NULL PRIMARY KEY,
    `layer_order` VARCHAR(32) NOT NULL DEFAULT 'NATIVE',
    `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

START TRANSACTION;

INSERT INTO `shop` (`id`, `npc_id`, `tag_name`, `type_shop`)
VALUES (112, 112, 'KANAO', 0)
ON DUPLICATE KEY UPDATE
    `npc_id` = VALUES(`npc_id`),
    `tag_name` = VALUES(`tag_name`),
    `type_shop` = VALUES(`type_shop`);

INSERT INTO `tab_shop` (`id`, `shop_id`, `NAME`)
VALUES
    (1120, 112, 'Cải trang'),
    (1121, 112, 'Hỗ Trợ')
ON DUPLICATE KEY UPDATE
    `shop_id` = VALUES(`shop_id`),
    `NAME` = VALUES(`NAME`);

-- Kanao is a shared two-way gateway. Preserve existing NPCs on both maps and
-- add only the missing Kanao placement so the migration is safe to rerun.
UPDATE `map_template`
SET `npcs` = CASE
    WHEN REPLACE(`npcs`, ' ', '') REGEXP '\\[112,514,432,-32000,[0-9]+\\]'
        THEN REGEXP_REPLACE(`npcs`, '\\[112,514,432,-32000,[0-9]+\\]', '[112,514,432]')
    WHEN LOCATE('[112,', REPLACE(`npcs`, ' ', '')) > 0 THEN `npcs`
    WHEN TRIM(`npcs`) = '[]' THEN '[[112,514,432]]'
    ELSE CONCAT(LEFT(`npcs`, CHAR_LENGTH(`npcs`) - 1), ',[112,514,432]]')
END
WHERE `id` = 0;

UPDATE `map_template`
SET `npcs` = CASE
    WHEN REPLACE(`npcs`, ' ', '') REGEXP '\\[112,59,360,-32000,[0-9]+\\]'
        THEN REGEXP_REPLACE(`npcs`, '\\[112,59,360,-32000,[0-9]+\\]', '[112,59,360]')
    WHEN LOCATE('[112,', REPLACE(`npcs`, ' ', '')) > 0 THEN `npcs`
    WHEN TRIM(`npcs`) = '[]' THEN '[[112,59,360]]'
    ELSE CONCAT(LEFT(`npcs`, CHAR_LENGTH(`npcs`) - 1), ',[112,59,360]]')
END
WHERE `id` = 187;

-- Client vẽ slot Head -> Leg -> Body. Lưu hình Leg -> Body -> Head vào ba slot
-- tương ứng để Head của Kanao được vẽ trên cùng mà không sửa client.
UPDATE `part` SET `DATA` = '[[25017,7,13],[0,0,0],[0,0,0]]'
WHERE `id` = 2132 AND `TYPE` = 0;
UPDATE `part` SET `DATA` = '[[0,0,0],[25015,0,-25],[0,0,0],[0,0,0],[0,0,0],[0,0,0],[0,0,0],[0,0,0],[0,0,0],[0,0,0],[0,0,0],[0,0,0],[0,0,0],[0,0,0],[0,0,0],[0,0,0],[0,0,0]]'
WHERE `id` = 2133 AND `TYPE` = 1;
UPDATE `part` SET `DATA` = '[[0,0,0],[25016,-1,-19],[0,0,0],[0,0,0],[0,0,0],[0,0,0],[0,0,0],[0,0,0],[0,0,0],[0,0,0],[0,0,0],[0,0,0],[0,0,0],[0,0,0]]'
WHERE `id` = 2134 AND `TYPE` = 2;
UPDATE `npc_template` SET `avatar` = 25014 WHERE `id` = 112;

INSERT INTO `admin_npc_render_config` (`npc_id`, `layer_order`)
VALUES (112, 'LEG_BODY_HEAD')
ON DUPLICATE KEY UPDATE `layer_order` = VALUES(`layer_order`);

COMMIT;
