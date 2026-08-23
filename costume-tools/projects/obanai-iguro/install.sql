-- Install Cải trang Obanai Iguro; IDs were allocated contiguously from the live team2026 database.
START TRANSACTION;

INSERT INTO part (id, `TYPE`, `DATA`) VALUES
(2144, 0, '[[25101,0,0],[25101,0,0],[25101,0,0]]'),
(2145, 1, '[[25084,-19,-36],[25085,-8,-37],[25125,-15,-32],[25087,-16,-34],[25088,-18,-32],[25089,-19,-37],[25090,-19,-36],[26006,-15,-32],[25092,-9,-30],[25093,-15,-40],[25094,-14,-28],[25095,-12,-38],[25096,-12,-45],[25097,-14,-26],[25098,-12,-44],[25099,-10,-41],[25100,-22,-38]]'),
(2146, 2, '[[25101,0,0],[25101,0,0],[25101,0,0],[25101,0,0],[25101,0,0],[25101,0,0],[25101,0,0],[25101,0,0],[25101,0,0],[25101,0,0],[25101,0,0],[25101,0,0],[25101,0,0],[25101,0,0]]')
ON DUPLICATE KEY UPDATE `TYPE`=VALUES(`TYPE`), `DATA`=VALUES(`DATA`);

INSERT INTO head_avatar (head_id, avatar_id) VALUES (2144, 26013)
ON DUPLICATE KEY UPDATE avatar_id=VALUES(avatar_id);

INSERT INTO item_template
(id, `TYPE`, gender, `NAME`, description, level, icon_id, part, is_up_to_up, power_require, gold, gem, head, body, leg)
VALUES
(2065, 5, 3, 'Cải trang Obanai Iguro', 'Cải trang thành Obanai Iguro', 1, 25102, -1, 0, 0, 0, 0, 2144, 2145, 2146)
ON DUPLICATE KEY UPDATE
`TYPE`=VALUES(`TYPE`), gender=VALUES(gender), `NAME`=VALUES(`NAME`),
description=VALUES(description), level=VALUES(level), icon_id=VALUES(icon_id),
part=VALUES(part), is_up_to_up=VALUES(is_up_to_up), power_require=VALUES(power_require),
gold=VALUES(gold), gem=VALUES(gem), head=VALUES(head), body=VALUES(body), leg=VALUES(leg);

DELETE FROM item_default_option WHERE item_template_id = 2065;

INSERT INTO item_shop
(tab_id, temp_id, is_new, is_sell, type_sell, cost, icon_spec, option_mode, create_time)
SELECT 1120, 2065, 1, 1, 1, 2000, 0, 0, NOW()
WHERE EXISTS (SELECT 1 FROM tab_shop WHERE id = 1120 AND shop_id = 112)
  AND NOT EXISTS (SELECT 1 FROM item_shop WHERE tab_id = 1120 AND temp_id = 2065);

COMMIT;
