-- Apply after the initial Khí Nguyên Trảm migrations on live databases.
-- New image IDs make Unity fetch the replacement skill and book artwork instead
-- of rendering a locally cached image with the previous ID.

START TRANSACTION;

UPDATE `skill_template`
SET `icon_id` = 14658
WHERE `nclass_id` = 0 AND `id` = 27;

UPDATE `item_template`
SET `icon_id` = CASE `id`
    WHEN 2219 THEN 14659
    WHEN 2220 THEN 14660
    WHEN 2221 THEN 14661
    WHEN 2222 THEN 14662
    WHEN 2223 THEN 14663
    WHEN 2224 THEN 14664
    WHEN 2225 THEN 14665
END
WHERE `id` BETWEEN 2219 AND 2225;

COMMIT;
