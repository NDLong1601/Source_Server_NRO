-- First approved batch of dedicated aura inventory icons.
-- Aura artwork for x1..x4 was imported during the 2026-09-02 rollout.
-- The one-off importer was retired after the deployed assets were verified.
-- The remaining aura items retain their previous icon until their own artwork
-- has been generated and imported.

START TRANSACTION;

UPDATE `item_template`
SET `icon_id` = CASE `id`
    WHEN 2154 THEN 26576
    WHEN 2155 THEN 26577
    WHEN 2156 THEN 26578
    WHEN 2157 THEN 26579
    WHEN 2158 THEN 26580
    WHEN 2159 THEN 26581
    WHEN 2160 THEN 26582
    WHEN 2161 THEN 26583
    WHEN 2162 THEN 26584
    WHEN 2163 THEN 26585
    WHEN 2164 THEN 26586
    WHEN 2165 THEN 26587
END
WHERE `id` BETWEEN 2154 AND 2165
  AND `TYPE` = 11;

COMMIT;
