-- Install Cải trang Douma; IDs were allocated contiguously from the live team2026 database.
START TRANSACTION;

DELETE FROM part WHERE id IN (2165, 2166, 2167);
INSERT INTO part (id, `TYPE`, `DATA`) VALUES
(2165, 0, '[[26056,0,0],[26056,0,0],[26056,0,0]]'),
(2166, 1, '[[26039,-20,-42],[26097,-14,-45],[26098,-19,-42],[26042,-14,-24],[26043,-16,-39],[26044,-16,-39],[26045,-14,-46],[26099,-19,-42],[26047,-8,-34],[26048,-12,-32],[26049,-6,-38],[26050,-10,-41],[26051,-6,-38],[26052,-18,-34],[26053,-12,-32],[26054,-15,-41],[26055,-14,-46]]'),
(2167, 2, '[[26056,0,0],[26056,0,0],[26056,0,0],[26056,0,0],[26056,0,0],[26056,0,0],[26056,0,0],[26056,0,0],[26056,0,0],[26056,0,0],[26056,0,0],[26056,0,0],[26056,0,0],[26056,0,0]]');

DELETE FROM head_avatar WHERE head_id = 2165;
INSERT INTO head_avatar (head_id, avatar_id) VALUES (2165, 26058);

INSERT INTO item_template
(id, `TYPE`, gender, `NAME`, description, level, icon_id, part, is_up_to_up, power_require, gold, gem, head, body, leg)
VALUES
(2072, 5, 3, 'Cải trang Douma', 'Cải trang thành Douma', 1, 26057, -1, 0, 0, 0, 0, 2165, 2166, 2167)
ON DUPLICATE KEY UPDATE
`TYPE`=VALUES(`TYPE`), gender=VALUES(gender), `NAME`=VALUES(`NAME`),
description=VALUES(description), level=VALUES(level), icon_id=VALUES(icon_id),
part=VALUES(part), is_up_to_up=VALUES(is_up_to_up), power_require=VALUES(power_require),
gold=VALUES(gold), gem=VALUES(gem), head=VALUES(head), body=VALUES(body), leg=VALUES(leg);

DELETE FROM item_default_option WHERE item_template_id = 2072;

INSERT INTO item_shop
(tab_id, temp_id, is_new, is_sell, type_sell, cost, icon_spec, option_mode, create_time)
SELECT 1120, 2072, 1, 1, 1, 2000, 0, 0, NOW()
WHERE EXISTS (SELECT 1 FROM tab_shop WHERE id = 1120 AND shop_id = 112)
  AND NOT EXISTS (SELECT 1 FROM item_shop WHERE tab_id = 1120 AND temp_id = 2072);

COMMIT;
