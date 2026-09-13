-- Install Cải trang Muzan Demon; IDs were allocated contiguously from the live team2026 database.
START TRANSACTION;

DELETE FROM part WHERE id IN (2177, 2178, 2179);
INSERT INTO part (id, `TYPE`, `DATA`) VALUES
(2177, 0, '[[26158,0,0],[26158,0,0],[26158,0,0]]'),
(2178, 1, '[[26141,-18,-44],[26172,-19,-44],[26143,-17,-39],[26144,-18,-36],[26145,-18,-36],[26146,-21,-34],[26147,-13,-42],[26148,-17,-39],[26149,-17,-39],[26150,-19,-45],[26151,-19,-41],[26152,-17,-45],[26153,-17,-39],[26154,-18,-36],[26155,-19,-45],[26156,-18,-44],[26157,-21,-34]]'),
(2179, 2, '[[26158,0,0],[26158,0,0],[26158,0,0],[26158,0,0],[26158,0,0],[26158,0,0],[26158,0,0],[26158,0,0],[26158,0,0],[26158,0,0],[26158,0,0],[26158,0,0],[26158,0,0],[26158,0,0]]');

DELETE FROM head_avatar WHERE head_id = 2177;
INSERT INTO head_avatar (head_id, avatar_id) VALUES (2177, 26160);

INSERT INTO item_template
(id, `TYPE`, gender, `NAME`, description, level, icon_id, part, is_up_to_up, power_require, gold, gem, head, body, leg)
VALUES
(2076, 5, 3, 'Cải trang Muzan Demon', 'Cải trang thành Muzan Demon', 1, 26159, -1, 0, 0, 0, 0, 2177, 2178, 2179)
ON DUPLICATE KEY UPDATE
`TYPE`=VALUES(`TYPE`), gender=VALUES(gender), `NAME`=VALUES(`NAME`),
description=VALUES(description), level=VALUES(level), icon_id=VALUES(icon_id),
part=VALUES(part), is_up_to_up=VALUES(is_up_to_up), power_require=VALUES(power_require),
gold=VALUES(gold), gem=VALUES(gem), head=VALUES(head), body=VALUES(body), leg=VALUES(leg);

DELETE FROM item_default_option WHERE item_template_id = 2076;

INSERT INTO item_shop
(tab_id, temp_id, is_new, is_sell, type_sell, cost, icon_spec, option_mode, create_time)
SELECT 1120, 2076, 1, 1, 1, 2000, 0, 0, NOW()
WHERE EXISTS (SELECT 1 FROM tab_shop WHERE id = 1120 AND shop_id = 112)
  AND NOT EXISTS (SELECT 1 FROM item_shop WHERE tab_id = 1120 AND temp_id = 2076);

COMMIT;
