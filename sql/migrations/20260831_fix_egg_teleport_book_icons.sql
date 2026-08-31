-- Correct all seven levels without editing/resizing PNG assets.
-- Explicit assignments are idempotent: reapplying cannot swap them back.
-- Deploy with DataGame.vsItem 57 so clients reload the icon-ID mapping.
START TRANSACTION;

UPDATE `item_template`
SET `icon_id` = CASE
    WHEN `id` BETWEEN 335 AND 341 THEN 26519 + `id` - 335
    WHEN `id` BETWEEN 488 AND 494 THEN 26491 + `id` - 488
END
WHERE `id` BETWEEN 335 AND 341 OR `id` BETWEEN 488 AND 494;

COMMIT;
