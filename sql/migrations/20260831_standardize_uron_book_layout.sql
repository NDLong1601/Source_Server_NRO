-- Uron filters one shared list by player gender. A common (gender=3) book
-- must follow ALL race-specific books, not merely the Earth subset.
-- Preserve row IDs, flags, currencies, prices and options.
START TRANSACTION;

SET @special_book_tail = (
    SELECT MIN(`create_time`) FROM `item_shop`
    WHERE `tab_id` = 12
      AND `temp_id` NOT BETWEEN 434 AND 440
      AND `temp_id` NOT BETWEEN 2233 AND 2239
);

UPDATE `item_shop`
SET `tab_id` = 12,
    `create_time` = TIMESTAMPADD(SECOND, -(CAST(`temp_id` AS SIGNED) - 2232), @special_book_tail)
WHERE `tab_id` IN (11,12) AND `temp_id` BETWEEN 2233 AND 2239;

UPDATE `item_shop`
SET `create_time` = TIMESTAMPADD(SECOND, -(CAST(`temp_id` AS SIGNED) - 434 + 8), @special_book_tail)
WHERE `tab_id` = 12 AND `temp_id` BETWEEN 434 AND 440;

COMMIT;
