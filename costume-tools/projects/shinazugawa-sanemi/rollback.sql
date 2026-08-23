-- Snapshot captured before installing the replacement on 2026-08-23.

START TRANSACTION;

INSERT INTO head_avatar (head_id, avatar_id)
VALUES (2099, 25032)
ON DUPLICATE KEY UPDATE avatar_id = VALUES(avatar_id);

DELETE FROM item_shop_option
WHERE item_shop_id IN (
    SELECT id FROM item_shop WHERE tab_id = 1120 AND temp_id = 2062
);
DELETE FROM item_shop
WHERE tab_id = 1120 AND temp_id = 2062;

INSERT INTO part (`id`, `TYPE`, `DATA`) VALUES
    (2099, 0, '[[250,0,0],[0,0,0],[0,0,0]]'),
    (2100, 1, '[[0,0,0],[252,0,0],[0,0,0],[0,0,0],[0,0,0],[0,0,0],[0,0,0],[0,0,0],[0,0,0],[0,0,0],[0,0,0],[0,0,0],[0,0,0],[0,0,0],[0,0,0],[0,0,0],[0,0,0]]'),
    (2101, 2, '[[0,0,0],[251,0,0],[0,0,0],[0,0,0],[0,0,0],[0,0,0],[0,0,0],[0,0,0],[0,0,0],[0,0,0],[0,0,0],[0,0,0],[0,0,0],[0,0,0]]')
ON DUPLICATE KEY UPDATE
    `TYPE` = VALUES(`TYPE`),
    `DATA` = VALUES(`DATA`);

INSERT INTO item_template
    (`id`, `TYPE`, gender, `NAME`, description, level, icon_id, part,
     is_up_to_up, power_require, gold, gem, head, body, leg)
VALUES
    (2062, 5, 3, 'Cải trang Shinazugawa Sanemi',
     'Cải trang thành Shinazugawa Sanemi', 1, 25001, -1,
     0, 0, 0, 0, 2099, 2100, 2101)
ON DUPLICATE KEY UPDATE
    `TYPE` = VALUES(`TYPE`),
    gender = VALUES(gender),
    `NAME` = VALUES(`NAME`),
    description = VALUES(description),
    level = VALUES(level),
    icon_id = VALUES(icon_id),
    part = VALUES(part),
    is_up_to_up = VALUES(is_up_to_up),
    power_require = VALUES(power_require),
    gold = VALUES(gold),
    gem = VALUES(gem),
    head = VALUES(head),
    body = VALUES(body),
    leg = VALUES(leg);

COMMIT;
