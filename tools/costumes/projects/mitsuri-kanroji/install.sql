-- Install Cải trang Mitsuri Kanroji; IDs were allocated contiguously from the live team2026 database.
START TRANSACTION;

INSERT INTO part (id, `TYPE`, `DATA`) VALUES
(2156, 0, '[[25186,0,0],[25186,0,0],[25186,0,0]]'),
(2157, 1, '[[25169,-17,-37],[25216,-7,-34],[25300,-13,-35],[25172,-10,-18],[25173,-12,-23],[25174,-13,-23],[25175,-12,-34],[26005,-13,-35],[25177,-10,-29],[25178,-11,-39],[25179,-11,-42],[25180,-11,-41],[25181,-10,-29],[25301,-13,-35],[25183,-9,-43],[25184,-12,-39],[25185,-12,-34]]'),
(2158, 2, '[[25186,0,0],[25186,0,0],[25186,0,0],[25186,0,0],[25186,0,0],[25186,0,0],[25186,0,0],[25186,0,0],[25186,0,0],[25186,0,0],[25186,0,0],[25186,0,0],[25186,0,0],[25186,0,0]]')
ON DUPLICATE KEY UPDATE `TYPE`=VALUES(`TYPE`), `DATA`=VALUES(`DATA`);

INSERT INTO head_avatar (head_id, avatar_id) VALUES (2156, 26017)
ON DUPLICATE KEY UPDATE avatar_id=VALUES(avatar_id);

INSERT INTO item_template
(id, `TYPE`, gender, `NAME`, description, level, icon_id, part, is_up_to_up, power_require, gold, gem, head, body, leg)
VALUES
(2069, 5, 3, 'Cải trang Mitsuri Kanroji', 'Cải trang thành Mitsuri Kanroji', 1, 25187, -1, 0, 0, 0, 0, 2156, 2157, 2158)
ON DUPLICATE KEY UPDATE
`TYPE`=VALUES(`TYPE`), gender=VALUES(gender), `NAME`=VALUES(`NAME`),
description=VALUES(description), level=VALUES(level), icon_id=VALUES(icon_id),
part=VALUES(part), is_up_to_up=VALUES(is_up_to_up), power_require=VALUES(power_require),
gold=VALUES(gold), gem=VALUES(gem), head=VALUES(head), body=VALUES(body), leg=VALUES(leg);

DELETE FROM item_default_option WHERE item_template_id = 2069;

INSERT INTO item_shop
(tab_id, temp_id, is_new, is_sell, type_sell, cost, icon_spec, option_mode, create_time)
SELECT 1120, 2069, 1, 1, 1, 2000, 0, 0, NOW()
WHERE EXISTS (SELECT 1 FROM tab_shop WHERE id = 1120 AND shop_id = 112)
  AND NOT EXISTS (SELECT 1 FROM item_shop WHERE tab_id = 1120 AND temp_id = 2069);

COMMIT;
