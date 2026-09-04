-- Chuyển toàn bộ vật phẩm Cải trang trong NPC SANTA sang option_mode = 2 (Mặc định + bổ sung)
-- Bao gồm các tab Cải trang của NPC Santa (tab 18, 52, 70, 47, 48, 66, 50), loại trừ Tiệm hớt tóc (tab 13).

START TRANSACTION;

UPDATE item_shop i
JOIN tab_shop ts ON ts.id = i.tab_id
JOIN shop s ON s.id = ts.shop_id
JOIN item_template t ON t.id = i.temp_id
SET i.option_mode = 2
WHERE (s.npc_id = 39 OR s.tag_name LIKE '%SANTA%')
  AND t.type = 5
  AND ts.id <> 13;

COMMIT;
