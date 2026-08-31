-- Keep custom skill books in a deterministic visual order.
-- ShopDAO reads item_shop by create_time DESC, so each timestamp below is
-- intentional: level 1 appears first and level 7 last within its book set.
START TRANSACTION;

-- The two chưởng book sets belong at the end of tab 11, after Sách Trói.
UPDATE `item_shop`
SET `create_time` = CASE `temp_id`
    WHEN 2219 THEN '2022-06-10 05:02:59'
    WHEN 2220 THEN '2022-06-10 05:02:58'
    WHEN 2221 THEN '2022-06-10 05:02:57'
    WHEN 2222 THEN '2022-06-10 05:02:56'
    WHEN 2223 THEN '2022-06-10 05:02:55'
    WHEN 2224 THEN '2022-06-10 05:02:54'
    WHEN 2225 THEN '2022-06-10 05:02:53'
    WHEN 2226 THEN '2022-06-10 05:02:52'
    WHEN 2227 THEN '2022-06-10 05:02:51'
    WHEN 2228 THEN '2022-06-10 05:02:50'
    WHEN 2229 THEN '2022-06-10 05:02:49'
    WHEN 2230 THEN '2022-06-10 05:02:48'
    WHEN 2231 THEN '2022-06-10 05:02:47'
    WHEN 2232 THEN '2022-06-10 05:02:46'
END
WHERE `tab_id` = 11 AND `temp_id` BETWEEN 2219 AND 2232;

-- Place the custom book before the shared shield, after all race-specific
-- books. Otherwise the shield incorrectly becomes first for Namec/Xayda.
SET @special_book_tail = (
    SELECT MIN(`create_time`) FROM `item_shop`
    WHERE `tab_id` = 12
      AND `temp_id` NOT BETWEEN 434 AND 440
      AND `temp_id` NOT BETWEEN 2233 AND 2239
);

UPDATE `item_shop`
SET
    `tab_id` = 12,
    `create_time` = TIMESTAMPADD(SECOND, -(CAST(`temp_id` AS SIGNED) - 2232), @special_book_tail)
WHERE `tab_id` IN (11,12) AND `temp_id` BETWEEN 2233 AND 2239;

UPDATE `item_shop`
SET `create_time` = TIMESTAMPADD(SECOND, -(CAST(`temp_id` AS SIGNED) - 434 + 8), @special_book_tail)
WHERE `tab_id` = 12 AND `temp_id` BETWEEN 434 AND 440;

COMMIT;
