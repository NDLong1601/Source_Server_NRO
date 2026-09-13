-- Install Cải trang Akaza; IDs were allocated contiguously from the live team2026 database.
START TRANSACTION;

DELETE FROM part WHERE id IN (2162, 2163, 2164);
INSERT INTO part (id, `TYPE`, `DATA`) VALUES
(2162, 0, '[[26036,0,0],[26036,0,0],[26036,0,0]]'),
(2163, 1, '[[26019,-12,-45],[26093,-10,-45],[26094,-16,-39],[26022,-11,-44],[26023,-12,-44],[26024,-14,-44],[26025,-10,-46],[26095,-16,-39],[26080,-15,-40],[26081,-21,-46],[26082,-14,-40],[26083,-11,-43],[26084,-20,-42],[26096,-16,-40],[26085,-18,-47],[26034,-12,-45],[26035,-16,-40]]'),
(2164, 2, '[[26036,0,0],[26036,0,0],[26036,0,0],[26036,0,0],[26036,0,0],[26036,0,0],[26036,0,0],[26036,0,0],[26036,0,0],[26036,0,0],[26036,0,0],[26036,0,0],[26036,0,0],[26036,0,0]]');

DELETE FROM head_avatar WHERE head_id = 2162;
INSERT INTO head_avatar (head_id, avatar_id) VALUES (2162, 26038);

INSERT INTO item_template
(id, `TYPE`, gender, `NAME`, description, level, icon_id, part, is_up_to_up, power_require, gold, gem, head, body, leg)
VALUES
(2071, 5, 3, 'Cải trang Akaza', 'Cải trang thành Akaza', 1, 26037, -1, 0, 0, 0, 0, 2162, 2163, 2164)
ON DUPLICATE KEY UPDATE
`TYPE`=VALUES(`TYPE`), gender=VALUES(gender), `NAME`=VALUES(`NAME`),
description=VALUES(description), level=VALUES(level), icon_id=VALUES(icon_id),
part=VALUES(part), is_up_to_up=VALUES(is_up_to_up), power_require=VALUES(power_require),
gold=VALUES(gold), gem=VALUES(gem), head=VALUES(head), body=VALUES(body), leg=VALUES(leg);

DELETE FROM item_default_option WHERE item_template_id = 2071;

INSERT INTO item_shop
(tab_id, temp_id, is_new, is_sell, type_sell, cost, icon_spec, option_mode, create_time)
SELECT 1120, 2071, 1, 1, 1, 2000, 0, 0, NOW()
WHERE EXISTS (SELECT 1 FROM tab_shop WHERE id = 1120 AND shop_id = 112)
  AND NOT EXISTS (SELECT 1 FROM item_shop WHERE tab_id = 1120 AND temp_id = 2071);

COMMIT;
