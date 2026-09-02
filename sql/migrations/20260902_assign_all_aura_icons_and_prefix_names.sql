-- Final dedicated inventory icon mapping for all 64 aura templates.
-- Safe to rerun: names are prefixed once and descriptions remain untouched.

START TRANSACTION;

UPDATE `item_template`
SET `icon_id` = 26576 + (`id` - 2154),
    `NAME` = CASE
        WHEN `NAME` LIKE 'Hào quang %' THEN `NAME`
        ELSE CONCAT('Hào quang ', `NAME`)
    END
WHERE `id` BETWEEN 2154 AND 2217
  AND `TYPE` = 11;

COMMIT;
