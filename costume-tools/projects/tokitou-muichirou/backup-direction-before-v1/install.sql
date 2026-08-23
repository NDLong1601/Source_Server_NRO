-- Install the Tokitou Muichirou full-body costume.
-- Target: Config.properties -> localhost:3307/team2026
-- Audited before install: item_template 0..2062, part 0..2137.

START TRANSACTION;

INSERT INTO part (`id`, `TYPE`, `DATA`)
SELECT 2138, 0, '[[25060,0,0],[25060,0,0],[25060,0,0]]'
WHERE NOT EXISTS (SELECT 1 FROM part WHERE id = 2138);
UPDATE part
SET `TYPE` = 0,
    `DATA` = '[[25060,0,0],[25060,0,0],[25060,0,0]]'
WHERE id = 2138;

INSERT INTO part (`id`, `TYPE`, `DATA`)
SELECT 2139, 1, '[[25043,-9,-36],[25044,-20,-33],[25045,-9,-24],[25046,-14,-17],[25047,-15,-24],[25048,-8,-27],[25049,-14,-36],[25050,-11,-18],[25051,-11,-33],[25052,-11,-33],[25053,-11,-26],[25054,-10,-34],[25055,-11,-36],[25056,-12,-25],[25057,-11,-31],[25058,-19,-36],[25059,-15,-22]]'
WHERE NOT EXISTS (SELECT 1 FROM part WHERE id = 2139);
UPDATE part
SET `TYPE` = 1,
    `DATA` = '[[25043,-9,-36],[25044,-20,-33],[25045,-9,-24],[25046,-14,-17],[25047,-15,-24],[25048,-8,-27],[25049,-14,-36],[25050,-11,-18],[25051,-11,-33],[25052,-11,-33],[25053,-11,-26],[25054,-10,-34],[25055,-11,-36],[25056,-12,-25],[25057,-11,-31],[25058,-19,-36],[25059,-15,-22]]'
WHERE id = 2139;

INSERT INTO part (`id`, `TYPE`, `DATA`)
SELECT 2140, 2, '[[25060,0,0],[25060,0,0],[25060,0,0],[25060,0,0],[25060,0,0],[25060,0,0],[25060,0,0],[25060,0,0],[25060,0,0],[25060,0,0],[25060,0,0],[25060,0,0],[25060,0,0],[25060,0,0]]'
WHERE NOT EXISTS (SELECT 1 FROM part WHERE id = 2140);
UPDATE part
SET `TYPE` = 2,
    `DATA` = '[[25060,0,0],[25060,0,0],[25060,0,0],[25060,0,0],[25060,0,0],[25060,0,0],[25060,0,0],[25060,0,0],[25060,0,0],[25060,0,0],[25060,0,0],[25060,0,0],[25060,0,0],[25060,0,0]]'
WHERE id = 2140;

INSERT INTO item_template
    (`id`, `TYPE`, gender, `NAME`, description, level, icon_id, part,
     is_up_to_up, power_require, gold, gem, head, body, leg)
VALUES
    (2063, 5, 3, 'Cải trang Tokitou Muichirou',
     'Cải trang thành Hà Trụ Tokitou Muichirou', 1, 25061, -1,
     0, 0, 0, 0, 2138, 2139, 2140)
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

INSERT INTO head_avatar (head_id, avatar_id)
VALUES (2138, 25042)
ON DUPLICATE KEY UPDATE avatar_id = VALUES(avatar_id);

-- No default item options were requested.
DELETE FROM item_default_option
WHERE item_template_id = 2063;

-- Reuse Kanao shop 112 and its existing "Cải trang" tab 1120.
INSERT INTO item_shop
    (tab_id, temp_id, is_new, is_sell, type_sell, cost, icon_spec, option_mode, create_time)
SELECT 1120, 2063, 1, 1, 1, 2000, 0, 0, NOW()
WHERE EXISTS (
    SELECT 1 FROM tab_shop WHERE id = 1120 AND shop_id = 112
)
AND NOT EXISTS (
    SELECT 1 FROM item_shop WHERE tab_id = 1120 AND temp_id = 2063
);

COMMIT;
