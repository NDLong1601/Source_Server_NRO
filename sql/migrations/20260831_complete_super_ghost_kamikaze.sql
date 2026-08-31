-- Hoàn thiện Siêu Ma Cảm Tử: tên Việt hóa, icon, sách học và thứ tự Uron.
-- Có thể chạy lại an toàn trên database đã có các custom skill trước đó.
START TRANSACTION;

UPDATE `skill_template`
SET `NAME` = 'Siêu Ma Cảm Tử',
    `icon_id` = 26554,
    `dam_info` = 'Tổng sát thương: #%',
    `skills` = '[{"power_require":50000000,"damage":180,"dx":280,"dy":70,"price":5000,"max_fight":1,"mana_use":12,"cool_down":38000,"id":214,"point":1,"info":"Siêu Ma Cảm Tử cấp 1 - 1 hồn ma"},{"power_require":100000000,"damage":220,"dx":310,"dy":75,"price":10000,"max_fight":2,"mana_use":12,"cool_down":36000,"id":215,"point":2,"info":"Siêu Ma Cảm Tử cấp 2 - 2 hồn ma"},{"power_require":250000000,"damage":260,"dx":340,"dy":80,"price":16000,"max_fight":3,"mana_use":11,"cool_down":34000,"id":216,"point":3,"info":"Siêu Ma Cảm Tử cấp 3 - 3 hồn ma"},{"power_require":500000000,"damage":310,"dx":370,"dy":85,"price":24000,"max_fight":4,"mana_use":11,"cool_down":32000,"id":217,"point":4,"info":"Siêu Ma Cảm Tử cấp 4 - 4 hồn ma"},{"power_require":900000000,"damage":360,"dx":400,"dy":90,"price":28000,"max_fight":5,"mana_use":10,"cool_down":30000,"id":218,"point":5,"info":"Siêu Ma Cảm Tử cấp 5 - 5 hồn ma"},{"power_require":1500000000,"damage":410,"dx":430,"dy":95,"price":30000,"max_fight":6,"mana_use":9,"cool_down":28000,"id":219,"point":6,"info":"Siêu Ma Cảm Tử cấp 6 - 6 hồn ma"},{"power_require":2500000000,"damage":460,"dx":460,"dy":100,"price":32000,"max_fight":7,"mana_use":8,"cool_down":26000,"id":220,"point":7,"info":"Siêu Ma Cảm Tử cấp 7 - 7 hồn ma"}]'
WHERE `nclass_id` = 2 AND `id` = 31;

INSERT INTO `item_template`
    (`id`, `TYPE`, `gender`, `NAME`, `description`, `level`, `icon_id`, `part`, `is_up_to_up`, `power_require`, `gold`, `gem`, `head`, `body`, `leg`)
VALUES
    (2240, 7, 2, 'Sách Siêu Ma Cảm Tử cấp 1', 'Dùng để học Siêu Ma Cảm Tử cấp 1.', 1, 26547, -1, 1, 0, 0, 100, -1, -1, -1),
    (2241, 7, 2, 'Sách Siêu Ma Cảm Tử cấp 2', 'Dùng để học Siêu Ma Cảm Tử cấp 2.', 2, 26548, -1, 1, 0, 0, 100, -1, -1, -1),
    (2242, 7, 2, 'Sách Siêu Ma Cảm Tử cấp 3', 'Dùng để học Siêu Ma Cảm Tử cấp 3.', 3, 26549, -1, 1, 0, 0, 100, -1, -1, -1),
    (2243, 7, 2, 'Sách Siêu Ma Cảm Tử cấp 4', 'Dùng để học Siêu Ma Cảm Tử cấp 4.', 4, 26550, -1, 1, 0, 0, 100, -1, -1, -1),
    (2244, 7, 2, 'Sách Siêu Ma Cảm Tử cấp 5', 'Dùng để học Siêu Ma Cảm Tử cấp 5.', 5, 26551, -1, 1, 0, 0, 100, -1, -1, -1),
    (2245, 7, 2, 'Sách Siêu Ma Cảm Tử cấp 6', 'Dùng để học Siêu Ma Cảm Tử cấp 6.', 6, 26552, -1, 1, 0, 0, 100, -1, -1, -1),
    (2246, 7, 2, 'Sách Siêu Ma Cảm Tử cấp 7', 'Dùng để học Siêu Ma Cảm Tử cấp 7.', 7, 26553, -1, 1, 0, 0, 100, -1, -1, -1)
ON DUPLICATE KEY UPDATE
    `TYPE`=VALUES(`TYPE`), `gender`=VALUES(`gender`), `NAME`=VALUES(`NAME`),
    `description`=VALUES(`description`), `level`=VALUES(`level`), `icon_id`=VALUES(`icon_id`),
    `part`=VALUES(`part`), `is_up_to_up`=VALUES(`is_up_to_up`), `power_require`=VALUES(`power_require`),
    `gold`=VALUES(`gold`), `gem`=VALUES(`gem`), `head`=VALUES(`head`), `body`=VALUES(`body`), `leg`=VALUES(`leg`);

DELETE `item_shop_option`
FROM `item_shop_option`
INNER JOIN `item_shop` ON `item_shop`.`id` = `item_shop_option`.`item_shop_id`
WHERE `item_shop`.`temp_id` BETWEEN 2240 AND 2246;

-- The item-option packet stores its template count in one unsigned byte.
-- IDs 0..254 are the complete supported range; ID 255 would make the count
-- 256 and be decoded by the client as zero.
DELETE FROM `item_option_template` WHERE `id` = 255;

DELETE FROM `item_shop` WHERE `temp_id` BETWEEN 2240 AND 2246;

INSERT INTO `item_shop`
    (`tab_id`, `temp_id`, `is_new`, `is_sell`, `type_sell`, `cost`, `icon_spec`, `option_mode`, `create_time`)
VALUES
    (12, 2240, 1, 1, 1, 100, 0, 1, '2022-06-10 04:59:29'),
    (12, 2241, 1, 1, 1, 120, 0, 1, '2022-06-10 04:59:28'),
    (12, 2242, 1, 1, 1, 140, 0, 1, '2022-06-10 04:59:27'),
    (12, 2243, 1, 1, 1, 160, 0, 1, '2022-06-10 04:59:26'),
    (12, 2244, 1, 1, 1, 180, 0, 1, '2022-06-10 04:59:25'),
    (12, 2245, 1, 1, 1, 200, 0, 1, '2022-06-10 04:59:24'),
    (12, 2246, 1, 1, 1, 220, 0, 1, '2022-06-10 04:59:23');

-- The common shield books must remain after every race-specific special book.
SET @special_book_tail = (
    SELECT MIN(`create_time`) FROM `item_shop`
    WHERE `tab_id` = 12
      AND `temp_id` NOT BETWEEN 434 AND 440
      AND `temp_id` NOT BETWEEN 2233 AND 2246
);

UPDATE `item_shop`
SET `tab_id` = 12,
    `create_time` = TIMESTAMPADD(SECOND, -(CAST(`temp_id` AS SIGNED) - 2239), @special_book_tail)
WHERE `temp_id` BETWEEN 2240 AND 2246;

UPDATE `item_shop`
SET `tab_id` = 12,
    `create_time` = TIMESTAMPADD(SECOND, -(CAST(`temp_id` AS SIGNED) - 2233 + 8), @special_book_tail)
WHERE `temp_id` BETWEEN 2233 AND 2239;

UPDATE `item_shop`
SET `create_time` = TIMESTAMPADD(SECOND, -(CAST(`temp_id` AS SIGNED) - 434 + 15), @special_book_tail)
WHERE `tab_id` = 12 AND `temp_id` BETWEEN 434 AND 440;

COMMIT;
