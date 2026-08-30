-- Seven sequential, Namek-only books for Hellzone Grenade (skill template 28).
-- IDs follow the previous custom-skill books so the item-template list remains
-- contiguous for clients that index templates by their ID. The Unity client
-- supplies this final seven-ID tail locally to keep the legacy wire cache to
-- its two 16-bit packets.

START TRANSACTION;

-- The backend template was created earlier with a placeholder icon. Publish
-- the new Hellzone skill-tab icon without touching the other custom skills.
UPDATE `skill_template`
SET `icon_id` = 14642
WHERE `nclass_id` = 1 AND `id` = 28;

INSERT INTO `item_template`
    (`id`, `TYPE`, `gender`, `NAME`, `description`, `level`, `icon_id`, `part`, `is_up_to_up`, `power_require`, `gold`, `gem`, `head`, `body`, `leg`)
VALUES
    (2226, 7, 1, 'HZG 1', '', 1, 14643, -1, 1, 0, 0, 100, -1, -1, -1),
    (2227, 7, 1, 'HZG 2', '', 2, 14644, -1, 1, 0, 0, 120, -1, -1, -1),
    (2228, 7, 1, 'HZG 3', '', 3, 14645, -1, 1, 0, 0, 140, -1, -1, -1),
    (2229, 7, 1, 'HZG 4', '', 4, 14646, -1, 1, 0, 0, 160, -1, -1, -1),
    (2230, 7, 1, 'HZG 5', '', 5, 14647, -1, 1, 0, 0, 180, -1, -1, -1),
    (2231, 7, 1, 'HZG 6', '', 6, 14648, -1, 1, 0, 0, 200, -1, -1, -1),
    (2232, 7, 1, 'HZG 7', '', 7, 14649, -1, 1, 0, 0, 220, -1, -1, -1)
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
WHERE `tab_id` = 11 AND `temp_id` BETWEEN 2226 AND 2232;

INSERT INTO `item_shop`
    (`tab_id`, `temp_id`, `is_new`, `is_sell`, `type_sell`, `cost`, `icon_spec`, `option_mode`, `create_time`)
VALUES
    (11, 2226, 1, 1, 1, 100, 0, 1, NOW()),
    (11, 2227, 1, 1, 1, 120, 0, 1, NOW()),
    (11, 2228, 1, 1, 1, 140, 0, 1, NOW()),
    (11, 2229, 1, 1, 1, 160, 0, 1, NOW()),
    (11, 2230, 1, 1, 1, 180, 0, 1, NOW()),
    (11, 2231, 1, 1, 1, 200, 0, 1, NOW()),
    (11, 2232, 1, 1, 1, 220, 0, 1, NOW());

COMMIT;
