-- Equippable aura items for the Teamobi NRO server.
-- Preconditions: 20260825 fishing migrations were applied and own IDs through 2153.
-- This migration owns item_template IDs 2154..2217 and does not overwrite any
-- existing item or named-image row when rerun.

START TRANSACTION;

SET @aura_item_start = 2154;
SET @aura_assets = '0,1,3,4,5,6,7,8,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,32,33,34,35,36,37,38,39,50,56,57,58,59,60,61,63,64,65,66,77,80,81,82,83,84,86,87,88,89,90,91,92,93,94';

-- The base dataset already has template IDs 0..63. They provide a stable
-- 64-row sequence without introducing an auxiliary table.
INSERT INTO item_template
    (id, `TYPE`, gender, `NAME`, description, level, icon_id, part, is_up_to_up, power_require, gold, gem, head, body, leg)
SELECT
    @aura_item_start + source.id,
    11,
    3,
    CONCAT('Hào Quang ', source.id + 1),
    CONCAT('Trang bị để đổi thành hào quang ', source.id + 1, ' (asset ',
        CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(@aura_assets, ',', source.id + 1), ',', -1) AS UNSIGNED), ').'),
    0,
    11239,
    CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(@aura_assets, ',', source.id + 1), ',', -1) AS UNSIGNED),
    0, 0, 0, 0, -1, -1, -1
FROM item_template source
WHERE source.id BETWEEN 0 AND 63
  AND NOT EXISTS (
      SELECT 1 FROM item_template existing
      WHERE existing.id = @aura_item_start + source.id
  )
ORDER BY source.id;

-- Add missing metadata for both aura layers. The IDs are allocated after the
-- current maximum so this remains safe for a database with custom image rows.
SET @aura_next_image_id = (SELECT COALESCE(MAX(id), 0) FROM img_by_name);
INSERT INTO img_by_name (id, `NAME`, n_frame)
SELECT
    @aura_next_image_id := @aura_next_image_id + 1,
    CONCAT('aura_', CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(@aura_assets, ',', source.id + 1), ',', -1) AS UNSIGNED), '_', layer.layer_id),
    CASE
        WHEN CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(@aura_assets, ',', source.id + 1), ',', -1) AS UNSIGNED) IN (3, 4) THEN 8
        ELSE 4
    END
FROM item_template source
CROSS JOIN (SELECT 0 AS layer_id UNION ALL SELECT 1) layer
WHERE source.id BETWEEN 0 AND 63
  AND NOT EXISTS (
      SELECT 1
      FROM img_by_name existing
      WHERE existing.`NAME` = CONCAT('aura_', CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(@aura_assets, ',', source.id + 1), ',', -1) AS UNSIGNED), '_', layer.layer_id)
  )
ORDER BY source.id, layer.layer_id;

COMMIT;
