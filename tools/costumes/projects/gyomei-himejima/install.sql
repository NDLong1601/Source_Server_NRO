-- Install Cải trang Gyomei Himejima; IDs were allocated contiguously from the live team2026 database.
START TRANSACTION;

INSERT INTO part (id, `TYPE`, `DATA`) VALUES
(2153, 0, '[[25166,0,0],[25166,0,0],[25166,0,0]]'),
(2154, 1, '[[25149,-17,-34],[25150,-9,-35],[25151,-14,-40],[25152,-11,-31],[25153,-11,-30],[25154,-11,-41],[25155,-12,-41],[26004,-14,-40],[25157,-12,-44],[25158,-11,-42],[25159,-8,-29],[25160,-11,-40],[25161,-12,-42],[25162,-9,-30],[25163,-12,-41],[25164,-5,-43],[25165,-10,-41]]'),
(2155, 2, '[[25166,0,0],[25166,0,0],[25166,0,0],[25166,0,0],[25166,0,0],[25166,0,0],[25166,0,0],[25166,0,0],[25166,0,0],[25166,0,0],[25166,0,0],[25166,0,0],[25166,0,0],[25166,0,0]]')
ON DUPLICATE KEY UPDATE `TYPE`=VALUES(`TYPE`), `DATA`=VALUES(`DATA`);

INSERT INTO head_avatar (head_id, avatar_id) VALUES (2153, 26016)
ON DUPLICATE KEY UPDATE avatar_id=VALUES(avatar_id);

INSERT INTO item_template
(id, `TYPE`, gender, `NAME`, description, level, icon_id, part, is_up_to_up, power_require, gold, gem, head, body, leg)
VALUES
(2068, 5, 3, 'Cải trang Gyomei Himejima', 'Cải trang thành Gyomei Himejima', 1, 25167, -1, 0, 0, 0, 0, 2153, 2154, 2155)
ON DUPLICATE KEY UPDATE
`TYPE`=VALUES(`TYPE`), gender=VALUES(gender), `NAME`=VALUES(`NAME`),
description=VALUES(description), level=VALUES(level), icon_id=VALUES(icon_id),
part=VALUES(part), is_up_to_up=VALUES(is_up_to_up), power_require=VALUES(power_require),
gold=VALUES(gold), gem=VALUES(gem), head=VALUES(head), body=VALUES(body), leg=VALUES(leg);

DELETE FROM item_default_option WHERE item_template_id = 2068;

INSERT INTO item_shop
(tab_id, temp_id, is_new, is_sell, type_sell, cost, icon_spec, option_mode, create_time)
SELECT 1120, 2068, 1, 1, 1, 2000, 0, 0, NOW()
WHERE EXISTS (SELECT 1 FROM tab_shop WHERE id = 1120 AND shop_id = 112)
  AND NOT EXISTS (SELECT 1 FROM item_shop WHERE tab_id = 1120 AND temp_id = 2068);

COMMIT;
