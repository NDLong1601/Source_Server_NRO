-- Adds Kanao's two empty shop tabs. Quest progress is stored in the existing
-- player.nhiem_vu_kol JSON column, so no player schema change is required.

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
    WHEN LOCATE('[112,', REPLACE(`npcs`, ' ', '')) > 0 THEN `npcs`
    WHEN TRIM(`npcs`) = '[]' THEN '[[112,514,432]]'
    ELSE CONCAT(LEFT(`npcs`, CHAR_LENGTH(`npcs`) - 1), ',[112,514,432]]')
END
WHERE `id` = 0;

UPDATE `map_template`
SET `npcs` = CASE
    WHEN LOCATE('[112,', REPLACE(`npcs`, ' ', '')) > 0 THEN `npcs`
    WHEN TRIM(`npcs`) = '[]' THEN '[[112,59,360]]'
    ELSE CONCAT(LEFT(`npcs`, CHAR_LENGTH(`npcs`) - 1), ',[112,59,360]]')
END
WHERE `id` = 187;

-- Icon 25010 is a fully transparent 18x20 (x1) Head proxy shipped in
-- data/icon/x1-x4. It gives the client a normal NPC head bounding box so the
-- name is drawn above Kanao instead of across her face.
UPDATE `part`
SET `DATA` = '[[25010,0,0],[25010,0,0],[25010,0,0]]'
WHERE `id` = 2132 AND `TYPE` = 0;

COMMIT;
