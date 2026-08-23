-- Replace the broken Shinazugawa Sanemi costume in-place.
-- Target: Config.properties -> localhost:3307/team2026
-- Reuses item 2062 and parts 2099/2100/2101 to preserve contiguous IDs.

START TRANSACTION;

UPDATE part
SET `TYPE` = 0,
    `DATA` = '[[25040,0,0],[25040,0,0],[25040,0,0]]'
WHERE id = 2099;

UPDATE part
SET `TYPE` = 1,
    `DATA` = '[[25023,-12,-36],[25024,-4,-33],[25025,-13,-30],[25026,-12,-35],[25027,-8,-34],[25028,-12,-35],[25029,-9,-35],[25030,-8,-27],[25031,-22,-36],[25032,-11,-34],[25033,-11,-30],[25034,-11,-24],[25035,-11,-35],[25036,-14,-23],[25037,-9,-36],[25038,-12,-36],[25039,-15,-23]]'
WHERE id = 2100;

UPDATE part
SET `TYPE` = 2,
    `DATA` = '[[25040,0,0],[25040,0,0],[25040,0,0],[25040,0,0],[25040,0,0],[25040,0,0],[25040,0,0],[25040,0,0],[25040,0,0],[25040,0,0],[25040,0,0],[25040,0,0],[25040,0,0],[25040,0,0]]'
WHERE id = 2101;

-- The equipped costume head part uses 25022 in character/profile panels.
INSERT INTO head_avatar (head_id, avatar_id)
VALUES (2099, 25022)
ON DUPLICATE KEY UPDATE avatar_id = VALUES(avatar_id);

UPDATE item_template
SET `TYPE` = 5,
    gender = 3,
    `NAME` = 'Cải trang Shinazugawa Sanemi',
    description = 'Cải trang thành Shinazugawa Sanemi',
    level = 1,
    icon_id = 25041,
    part = -1,
    is_up_to_up = 0,
    power_require = 0,
    gold = 0,
    gem = 0,
    head = 2099,
    body = 2100,
    leg = 2101
WHERE id = 2062;

-- The user will configure default options later.
DELETE FROM item_default_option
WHERE item_template_id = 2062;

-- Reuse Kanao shop 112 and its existing "Cải trang" tab 1120.
-- Match the current tab convention: new, sellable, type_sell=1, cost=2000.
INSERT INTO item_shop
    (tab_id, temp_id, is_new, is_sell, type_sell, cost, icon_spec, option_mode, create_time)
SELECT 1120, 2062, 1, 1, 1, 2000, 0, 0, NOW()
WHERE EXISTS (
    SELECT 1 FROM tab_shop WHERE id = 1120 AND shop_id = 112
)
AND NOT EXISTS (
    SELECT 1 FROM item_shop WHERE tab_id = 1120 AND temp_id = 2062
);

COMMIT;
