-- Seven sequential, Earth-only books for skill template 27 (Khí Nguyên Trảm).
-- Item IDs are contiguous after the live item_template range 0..2218 so
-- ItemService.getTemplate(id), which indexes a list by template ID, stays valid.
-- Uron shop 4 / tab 11 is the existing "Sách<>Chưởng" tab.

START TRANSACTION;

INSERT INTO `item_template`
    (`id`, `TYPE`, `gender`, `NAME`, `description`, `level`, `icon_id`, `part`, `is_up_to_up`, `power_require`, `gold`, `gem`, `head`, `body`, `leg`)
VALUES
    -- The legacy client caches all templates in exactly two 16-bit packets.
    -- These short labels keep the second packet below 65,535 bytes; the book
    -- artwork itself conveys the matching level.
    (2219, 7, 0, 'KNT 1', '', 1, 14659, -1, 1, 0, 0, 100, -1, -1, -1),
    (2220, 7, 0, 'KNT 2', '', 2, 14660, -1, 1, 0, 0, 120, -1, -1, -1),
    (2221, 7, 0, 'KNT 3', '', 3, 14661, -1, 1, 0, 0, 140, -1, -1, -1),
    (2222, 7, 0, 'KNT 4', '', 4, 14662, -1, 1, 0, 0, 160, -1, -1, -1),
    (2223, 7, 0, 'KNT 5', '', 5, 14663, -1, 1, 0, 0, 180, -1, -1, -1),
    (2224, 7, 0, 'KNT 6', '', 6, 14664, -1, 1, 0, 0, 200, -1, -1, -1),
    (2225, 7, 0, 'KNT 7', '', 7, 14665, -1, 1, 0, 0, 220, -1, -1, -1)
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

-- Recreate only this seven-book subset so price and tab data stay idempotent.
DELETE FROM `item_shop`
WHERE `tab_id` = 11 AND `temp_id` BETWEEN 2219 AND 2225;

INSERT INTO `item_shop`
    (`tab_id`, `temp_id`, `is_new`, `is_sell`, `type_sell`, `cost`, `icon_spec`, `option_mode`, `create_time`)
VALUES
    (11, 2219, 1, 1, 1, 100, 0, 1, NOW()),
    (11, 2220, 1, 1, 1, 120, 0, 1, NOW()),
    (11, 2221, 1, 1, 1, 140, 0, 1, NOW()),
    (11, 2222, 1, 1, 1, 160, 0, 1, NOW()),
    (11, 2223, 1, 1, 1, 180, 0, 1, NOW()),
    (11, 2224, 1, 1, 1, 200, 0, 1, NOW()),
    (11, 2225, 1, 1, 1, 220, 0, 1, NOW());

-- The seven new templates leave the legacy item-cache transport only a few
-- bytes over its 16-bit limit. Keep the existing rename-card meaning while
-- freeing the bytes required to publish all seven book templates safely.
UPDATE `item_template`
SET `description` = 'Đổi tên.'
WHERE `id` = 2218 AND `NAME` = 'Thẻ đổi tên';

COMMIT;
