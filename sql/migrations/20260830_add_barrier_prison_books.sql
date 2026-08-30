-- Seven sequential, Earth-only books for Barrier Prison (skill template 29).
-- IDs remain contiguous after Hellzone Grenade so legacy clients can add the
-- complete 2226..2239 tail locally without overflowing the item-cache packet.

START TRANSACTION;

-- Publish the dedicated Barrier Prison skill-tab icon supplied with this feature.
UPDATE `skill_template`
SET `icon_id` = 14650
WHERE `nclass_id` = 0 AND `id` = 29;

INSERT INTO `item_template`
    (`id`, `TYPE`, `gender`, `NAME`, `description`, `level`, `icon_id`, `part`, `is_up_to_up`, `power_require`, `gold`, `gem`, `head`, `body`, `leg`)
VALUES
    (2233, 7, 0, 'BP 1', '', 1, 14651, -1, 1, 0, 0, 100, -1, -1, -1),
    (2234, 7, 0, 'BP 2', '', 2, 14652, -1, 1, 0, 0, 120, -1, -1, -1),
    (2235, 7, 0, 'BP 3', '', 3, 14653, -1, 1, 0, 0, 140, -1, -1, -1),
    (2236, 7, 0, 'BP 4', '', 4, 14654, -1, 1, 0, 0, 160, -1, -1, -1),
    (2237, 7, 0, 'BP 5', '', 5, 14655, -1, 1, 0, 0, 180, -1, -1, -1),
    (2238, 7, 0, 'BP 6', '', 6, 14656, -1, 1, 0, 0, 200, -1, -1, -1),
    (2239, 7, 0, 'BP 7', '', 7, 14657, -1, 1, 0, 0, 220, -1, -1, -1)
ON DUPLICATE KEY UPDATE
    `TYPE` = VALUES(`TYPE`),
    `gender` = VALUES(`gender`),
    `NAME` = VALUES(`NAME`),
    `description` = VALUES(`description`),
    `level` = VALUES(`level`),
    `icon_id` = VALUES(`icon_id`),
    `part` = VALUES(`part`),
    `is_up_to_up` = VALUES(`is_up_to_up`),
    `power_require` = VALUES(`power_require`),
    `gold` = VALUES(`gold`),
    `gem` = VALUES(`gem`),
    `head` = VALUES(`head`),
    `body` = VALUES(`body`),
    `leg` = VALUES(`leg`);

DELETE FROM `item_shop`
WHERE `tab_id` = 11 AND `temp_id` BETWEEN 2233 AND 2239;

INSERT INTO `item_shop`
    (`tab_id`, `temp_id`, `is_new`, `is_sell`, `type_sell`, `cost`, `icon_spec`, `option_mode`, `create_time`)
VALUES
    (11, 2233, 1, 1, 1, 100, 0, 1, NOW()),
    (11, 2234, 1, 1, 1, 120, 0, 1, NOW()),
    (11, 2235, 1, 1, 1, 140, 0, 1, NOW()),
    (11, 2236, 1, 1, 1, 160, 0, 1, NOW()),
    (11, 2237, 1, 1, 1, 180, 0, 1, NOW()),
    (11, 2238, 1, 1, 1, 200, 0, 1, NOW()),
    (11, 2239, 1, 1, 1, 220, 0, 1, NOW());

COMMIT;
