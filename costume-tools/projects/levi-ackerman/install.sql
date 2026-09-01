-- Install Cải trang Levi Ackerman; no item_shop row was requested.
START TRANSACTION;

DELETE FROM part WHERE id IN (2198, 2199, 2200);
INSERT INTO part (id, `TYPE`, `DATA`) VALUES
(2198, 0, '[[26572,0,0],[26572,0,0],[26572,0,0]]'),
(2199, 1, '[[26555,-9,-43],[26556,-7,-47],[26557,-11,-51],[26558,-10,-34],[26559,-17,-28],[26560,-18,-30],[26561,-9,-37],[26562,-27,-36],[26563,-12,-29],[26564,-13,-33],[26565,-9,-35],[26566,-16,-35],[26567,-17,-34],[26568,-14,-38],[26569,-19,-41],[26570,-18,-43],[26571,-18,-26]]'),
(2200, 2, '[[26572,0,0],[26572,0,0],[26572,0,0],[26572,0,0],[26572,0,0],[26572,0,0],[26572,0,0],[26572,0,0],[26572,0,0],[26572,0,0],[26572,0,0],[26572,0,0],[26572,0,0],[26572,0,0]]');

DELETE FROM head_avatar WHERE head_id = 2198;
INSERT INTO head_avatar (head_id, avatar_id) VALUES (2198, 26574);

INSERT INTO item_template
(id, `TYPE`, gender, `NAME`, description, level, icon_id, part, is_up_to_up, power_require, gold, gem, head, body, leg)
VALUES
(2247, 5, 3, 'Cải trang Levi Ackerman', 'Cải trang Levi Ackerman theo ảnh tham chiếu', 1, 26573, -1, 0, 0, 0, 0, 2198, 2199, 2200)
ON DUPLICATE KEY UPDATE
`TYPE`=VALUES(`TYPE`), gender=VALUES(gender), `NAME`=VALUES(`NAME`),
description=VALUES(description), level=VALUES(level), icon_id=VALUES(icon_id), part=VALUES(part), is_up_to_up=VALUES(is_up_to_up), power_require=VALUES(power_require),
gold=VALUES(gold), gem=VALUES(gem), head=VALUES(head), body=VALUES(body), leg=VALUES(leg);

DELETE FROM item_default_option WHERE item_template_id = 2247;
-- Add an item_shop/admin/gift distribution separately when requested.
COMMIT;
