-- Install Cải trang Rengoku; IDs were allocated contiguously from the live team2026 database.
START TRANSACTION;

INSERT INTO part (id, `TYPE`, `DATA`) VALUES
(2147, 0, '[[25121,0,0],[25121,0,0],[25121,0,0]]'),
(2148, 1, '[[25104,-24,-35],[25126,-9,-36],[25127,-15,-33],[25107,-14,-24],[25108,-17,-34],[25109,-18,-31],[25110,-16,-38],[26002,-15,-33],[25112,-12,-32],[25113,-16,-43],[25114,-12,-20],[25115,-9,-33],[25116,-10,-23],[25117,-15,-30],[25118,-15,-37],[25119,-21,-39],[25120,-17,-23]]'),
(2149, 2, '[[25121,0,0],[25121,0,0],[25121,0,0],[25121,0,0],[25121,0,0],[25121,0,0],[25121,0,0],[25121,0,0],[25121,0,0],[25121,0,0],[25121,0,0],[25121,0,0],[25121,0,0],[25121,0,0]]')
ON DUPLICATE KEY UPDATE `TYPE`=VALUES(`TYPE`), `DATA`=VALUES(`DATA`);

INSERT INTO head_avatar (head_id, avatar_id) VALUES (2147, 26014)
ON DUPLICATE KEY UPDATE avatar_id=VALUES(avatar_id);

INSERT INTO item_template
(id, `TYPE`, gender, `NAME`, description, level, icon_id, part, is_up_to_up, power_require, gold, gem, head, body, leg)
VALUES
(2066, 5, 3, 'Cải trang Rengoku', 'Cải trang thành Rengoku', 1, 25122, -1, 0, 0, 0, 0, 2147, 2148, 2149)
ON DUPLICATE KEY UPDATE
`TYPE`=VALUES(`TYPE`), gender=VALUES(gender), `NAME`=VALUES(`NAME`),
description=VALUES(description), level=VALUES(level), icon_id=VALUES(icon_id),
part=VALUES(part), is_up_to_up=VALUES(is_up_to_up), power_require=VALUES(power_require),
gold=VALUES(gold), gem=VALUES(gem), head=VALUES(head), body=VALUES(body), leg=VALUES(leg);

DELETE FROM item_default_option WHERE item_template_id = 2066;

INSERT INTO item_shop
(tab_id, temp_id, is_new, is_sell, type_sell, cost, icon_spec, option_mode, create_time)
SELECT 1120, 2066, 1, 1, 1, 2000, 0, 0, NOW()
WHERE EXISTS (SELECT 1 FROM tab_shop WHERE id = 1120 AND shop_id = 112)
  AND NOT EXISTS (SELECT 1 FROM item_shop WHERE tab_id = 1120 AND temp_id = 2066);

COMMIT;
