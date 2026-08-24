-- Install Cải trang Kokushibo; IDs were allocated contiguously from the live team2026 database.
START TRANSACTION;

DELETE FROM part WHERE id IN (2168, 2169, 2170);
INSERT INTO part (id, `TYPE`, `DATA`) VALUES
(2168, 0, '[[26076,0,0],[26076,0,0],[26076,0,0]]'),
(2169, 1, '[[26059,-13,-42],[26060,-16,-37],[26061,-6,-27],[26062,-8,-29],[26063,-8,-27],[26064,-8,-41],[26065,-8,-40],[26066,-6,-27],[26089,-11,-22],[26090,-13,-38],[26069,-3,-25],[26070,-11,-33],[26091,-11,-37],[26100,-14,-40],[26092,-14,-40],[26074,-14,-38],[26075,-8,-40]]'),
(2170, 2, '[[26076,0,0],[26076,0,0],[26076,0,0],[26076,0,0],[26076,0,0],[26076,0,0],[26076,0,0],[26076,0,0],[26076,0,0],[26076,0,0],[26076,0,0],[26076,0,0],[26076,0,0],[26076,0,0]]');

DELETE FROM head_avatar WHERE head_id = 2168;
INSERT INTO head_avatar (head_id, avatar_id) VALUES (2168, 26078);

INSERT INTO item_template
(id, `TYPE`, gender, `NAME`, description, level, icon_id, part, is_up_to_up, power_require, gold, gem, head, body, leg)
VALUES
(2073, 5, 3, 'Cải trang Kokushibo', 'Cải trang thành Kokushibo', 1, 26077, -1, 0, 0, 0, 0, 2168, 2169, 2170)
ON DUPLICATE KEY UPDATE
`TYPE`=VALUES(`TYPE`), gender=VALUES(gender), `NAME`=VALUES(`NAME`),
description=VALUES(description), level=VALUES(level), icon_id=VALUES(icon_id),
part=VALUES(part), is_up_to_up=VALUES(is_up_to_up), power_require=VALUES(power_require),
gold=VALUES(gold), gem=VALUES(gem), head=VALUES(head), body=VALUES(body), leg=VALUES(leg);

DELETE FROM item_default_option WHERE item_template_id = 2073;

INSERT INTO item_shop
(tab_id, temp_id, is_new, is_sell, type_sell, cost, icon_spec, option_mode, create_time)
SELECT 1120, 2073, 1, 1, 1, 2000, 0, 0, NOW()
WHERE EXISTS (SELECT 1 FROM tab_shop WHERE id = 1120 AND shop_id = 112)
  AND NOT EXISTS (SELECT 1 FROM item_shop WHERE tab_id = 1120 AND temp_id = 2073);

COMMIT;
