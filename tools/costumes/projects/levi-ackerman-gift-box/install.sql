-- Deploy Cải trang Levi Ackerman and its one-use test gift box.
CREATE TABLE IF NOT EXISTS gift_box_config (
  box_template_id INT NOT NULL PRIMARY KEY,
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  min_empty_slots INT NOT NULL DEFAULT 1,
  consume_quantity INT NOT NULL DEFAULT 1,
  draw_count INT NOT NULL DEFAULT 1,
  config_json TEXT NOT NULL,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

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
(2247, 5, 3, 'Cải trang Levi Ackerman', 'Cải trang Levi Ackerman theo ảnh tham chiếu', 1, 26573, -1, 0, 0, 0, 0, 2198, 2199, 2200),
(2248, 29, 3, 'Hộp quà Levi Ackerman', 'Mở ra nhận Cải trang Levi Ackerman vĩnh viễn.', 1, 26575, -1, 0, 0, 0, 0, -1, -1, -1)
ON DUPLICATE KEY UPDATE
`TYPE`=VALUES(`TYPE`), gender=VALUES(gender), `NAME`=VALUES(`NAME`), description=VALUES(description),
level=VALUES(level), icon_id=VALUES(icon_id), part=VALUES(part), is_up_to_up=VALUES(is_up_to_up),
power_require=VALUES(power_require), gold=VALUES(gold), gem=VALUES(gem), head=VALUES(head), body=VALUES(body), leg=VALUES(leg);

DELETE FROM item_default_option WHERE item_template_id IN (2247, 2248);

INSERT INTO gift_box_config
(box_template_id, enabled, min_empty_slots, consume_quantity, draw_count, config_json)
VALUES
(2248, 1, 1, 1, 1, '{"boxTemplateId":2248,"enabled":true,"minEmptySlots":1,"consumeQuantity":1,"drawCount":1,"rewards":[{"itemId":2247,"weight":1,"quantityMin":1,"quantityMax":1,"gender":3,"initBaseOptions":false,"useDefaultOptions":true,"options":[],"expiry":[{"mode":"permanent","weight":1,"daysMin":0,"daysMax":0}]}]}')
ON DUPLICATE KEY UPDATE
enabled=VALUES(enabled), min_empty_slots=VALUES(min_empty_slots), consume_quantity=VALUES(consume_quantity),
draw_count=VALUES(draw_count), config_json=VALUES(config_json);

INSERT INTO giftcode (code, count_left, detail, datecreate, expired)
SELECT 'LEVIA7Q9T2', 1, '[{"id":2248,"quantity":1,"useDefaultOptions":false,"options":[]}]',
       '2026-09-01 00:00:00', '2026-09-08 23:59:59'
WHERE NOT EXISTS (SELECT 1 FROM giftcode WHERE code = 'LEVIA7Q9T2');

COMMIT;
