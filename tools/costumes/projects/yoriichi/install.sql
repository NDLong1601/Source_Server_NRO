-- Install Cải trang Yoriichi; IDs were allocated contiguously from the live team2026 database.
START TRANSACTION;

DELETE FROM part WHERE id IN (2171, 2172, 2173);
INSERT INTO part (id, `TYPE`, `DATA`) VALUES
(2171, 0, '[[26118,0,0],[26118,0,0],[26118,0,0]]'),
(2172, 1, '[[26101,-16,-46],[26161,-19,-44],[26173,-13,-36],[26162,-17,-24],[26163,-13,-36],[26164,-14,-41],[26165,-11,-44],[26174,-15,-46],[26109,-14,-44],[26110,-19,-43],[26111,-15,-29],[26112,-13,-45],[26113,-17,-44],[26114,-17,-47],[26115,-21,-46],[26116,-13,-30],[26166,-13,-37]]'),
(2173, 2, '[[26118,0,0],[26118,0,0],[26118,0,0],[26118,0,0],[26118,0,0],[26118,0,0],[26118,0,0],[26118,0,0],[26118,0,0],[26118,0,0],[26118,0,0],[26118,0,0],[26118,0,0],[26118,0,0]]');

DELETE FROM head_avatar WHERE head_id = 2171;
INSERT INTO head_avatar (head_id, avatar_id) VALUES (2171, 26120);

INSERT INTO item_template
(id, `TYPE`, gender, `NAME`, description, level, icon_id, part, is_up_to_up, power_require, gold, gem, head, body, leg)
VALUES
(2074, 5, 3, 'Cải trang Yoriichi', 'Cải trang thành Yoriichi', 1, 26119, -1, 0, 0, 0, 0, 2171, 2172, 2173)
ON DUPLICATE KEY UPDATE
`TYPE`=VALUES(`TYPE`), gender=VALUES(gender), `NAME`=VALUES(`NAME`),
description=VALUES(description), level=VALUES(level), icon_id=VALUES(icon_id),
part=VALUES(part), is_up_to_up=VALUES(is_up_to_up), power_require=VALUES(power_require),
gold=VALUES(gold), gem=VALUES(gem), head=VALUES(head), body=VALUES(body), leg=VALUES(leg);

DELETE FROM item_default_option WHERE item_template_id = 2074;

INSERT INTO item_shop
(tab_id, temp_id, is_new, is_sell, type_sell, cost, icon_spec, option_mode, create_time)
SELECT 1120, 2074, 1, 1, 1, 2000, 0, 0, NOW()
WHERE EXISTS (SELECT 1 FROM tab_shop WHERE id = 1120 AND shop_id = 112)
  AND NOT EXISTS (SELECT 1 FROM item_shop WHERE tab_id = 1120 AND temp_id = 2074);

COMMIT;
