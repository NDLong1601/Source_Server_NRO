-- Đưa Dr Drief vào Lãnh địa Bang Hội để mở menu hợp đồng tuần và tập hợp.
-- Có thể chạy lại an toàn nếu NPC đã được thêm trước đó.

UPDATE `map_template`
SET `npcs` = CASE
    WHEN TRIM(`npcs`) = '[]' THEN '[[10,659,744]]'
    WHEN RIGHT(TRIM(`npcs`), 1) = ']' THEN CONCAT(
        LEFT(TRIM(`npcs`), CHAR_LENGTH(TRIM(`npcs`)) - 1),
        ',[10,659,744]]'
    )
    ELSE `npcs`
END
WHERE `id` = 153
  AND TRIM(`npcs`) NOT LIKE '%[10,%';

-- Reposition a Dr. Brief entry that was installed by an older revision.
UPDATE `map_template`
SET `npcs` = REPLACE(`npcs`, '[10,520,432]', '[10,659,744]')
WHERE `id` = 153
  AND `npcs` LIKE '%[10,520,432]%';

-- Đồng bộ metadata cửa hàng bang hội sang Dr. Brief.
UPDATE `shop`
SET `npc_id` = 10
WHERE `tag_name` = 'SHOP_CLAN';
