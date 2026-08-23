-- Install Cải trang Shinobu Kocho; IDs were allocated contiguously from the live team2026 database.
START TRANSACTION;

INSERT INTO part (id, `TYPE`, `DATA`) VALUES
(2159, 0, '[[25206,0,0],[25206,0,0],[25206,0,0]]'),
(2160, 1, '[[25189,-21,-33],[25208,-7,-38],[25209,-20,-43],[25210,-12,-29],[25211,-12,-29],[25212,-13,-41],[25213,-10,-40],[25225,-20,-43],[25197,-8,-30],[25198,-10,-30],[25199,-11,-41],[25200,-11,-42],[25201,-12,-42],[25214,-20,-43],[25203,-12,-41],[25204,-7,-42],[25215,-13,-41]]'),
(2161, 2, '[[25206,0,0],[25206,0,0],[25206,0,0],[25206,0,0],[25206,0,0],[25206,0,0],[25206,0,0],[25206,0,0],[25206,0,0],[25206,0,0],[25206,0,0],[25206,0,0],[25206,0,0],[25206,0,0]]')
ON DUPLICATE KEY UPDATE `TYPE`=VALUES(`TYPE`), `DATA`=VALUES(`DATA`);

INSERT INTO head_avatar (head_id, avatar_id) VALUES (2159, 26018)
ON DUPLICATE KEY UPDATE avatar_id=VALUES(avatar_id);

INSERT INTO item_template
(id, `TYPE`, gender, `NAME`, description, level, icon_id, part, is_up_to_up, power_require, gold, gem, head, body, leg)
VALUES
(2070, 5, 3, 'Cải trang Shinobu Kocho', 'Cải trang thành Shinobu Kocho', 1, 25207, -1, 0, 0, 0, 0, 2159, 2160, 2161)
ON DUPLICATE KEY UPDATE
`TYPE`=VALUES(`TYPE`), gender=VALUES(gender), `NAME`=VALUES(`NAME`),
description=VALUES(description), level=VALUES(level), icon_id=VALUES(icon_id),
part=VALUES(part), is_up_to_up=VALUES(is_up_to_up), power_require=VALUES(power_require),
gold=VALUES(gold), gem=VALUES(gem), head=VALUES(head), body=VALUES(body), leg=VALUES(leg);

DELETE FROM item_default_option WHERE item_template_id = 2070;

INSERT INTO item_shop
(tab_id, temp_id, is_new, is_sell, type_sell, cost, icon_spec, option_mode, create_time)
SELECT 1120, 2070, 1, 1, 1, 2000, 0, 0, NOW()
WHERE EXISTS (SELECT 1 FROM tab_shop WHERE id = 1120 AND shop_id = 112)
  AND NOT EXISTS (SELECT 1 FROM item_shop WHERE tab_id = 1120 AND temp_id = 2070);

COMMIT;
