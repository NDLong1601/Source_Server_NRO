-- Install Cải trang Tomioka Giyuu; IDs were allocated contiguously from the live team2026 database.
START TRANSACTION;

INSERT INTO part (id, `TYPE`, `DATA`) VALUES
(2141, 0, '[[25081,0,0],[25081,0,0],[25081,0,0]]'),
(2142, 1, '[[25064,-22,-38],[25123,-4,-35],[25124,-15,-28],[25067,-13,-20],[25068,-17,-28],[25069,-16,-30],[25070,-12,-37],[26008,-16,-26],[25072,-11,-36],[25073,-13,-36],[25074,-11,-23],[25075,-12,-29],[25076,-7,-28],[25077,-16,-32],[25078,-18,-36],[25079,-16,-37],[25080,-16,-18]]'),
(2143, 2, '[[25081,0,0],[25081,0,0],[25081,0,0],[25081,0,0],[25081,0,0],[25081,0,0],[25081,0,0],[25081,0,0],[25081,0,0],[25081,0,0],[25081,0,0],[25081,0,0],[25081,0,0],[25081,0,0]]')
ON DUPLICATE KEY UPDATE `TYPE`=VALUES(`TYPE`), `DATA`=VALUES(`DATA`);

INSERT INTO head_avatar (head_id, avatar_id) VALUES (2141, 26012)
ON DUPLICATE KEY UPDATE avatar_id=VALUES(avatar_id);

INSERT INTO item_template
(id, `TYPE`, gender, `NAME`, description, level, icon_id, part, is_up_to_up, power_require, gold, gem, head, body, leg)
VALUES
(2064, 5, 3, 'Cải trang Tomioka Giyuu', 'Cải trang thành Tomioka Giyuu', 1, 25082, -1, 0, 0, 0, 0, 2141, 2142, 2143)
ON DUPLICATE KEY UPDATE
`TYPE`=VALUES(`TYPE`), gender=VALUES(gender), `NAME`=VALUES(`NAME`),
description=VALUES(description), level=VALUES(level), icon_id=VALUES(icon_id),
part=VALUES(part), is_up_to_up=VALUES(is_up_to_up), power_require=VALUES(power_require),
gold=VALUES(gold), gem=VALUES(gem), head=VALUES(head), body=VALUES(body), leg=VALUES(leg);

DELETE FROM item_default_option WHERE item_template_id = 2064;

INSERT INTO item_shop
(tab_id, temp_id, is_new, is_sell, type_sell, cost, icon_spec, option_mode, create_time)
SELECT 1120, 2064, 1, 1, 1, 2000, 0, 0, NOW()
WHERE EXISTS (SELECT 1 FROM tab_shop WHERE id = 1120 AND shop_id = 112)
  AND NOT EXISTS (SELECT 1 FROM item_shop WHERE tab_id = 1120 AND temp_id = 2064);

COMMIT;
