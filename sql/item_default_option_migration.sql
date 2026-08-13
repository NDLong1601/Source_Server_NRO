-- Item option presets shared by shops, task rewards and item drops.
-- Existing shop rows are kept CUSTOM during migration; newly-created rows
-- inherit the item preset by default.

CREATE TABLE IF NOT EXISTS item_default_option (
  item_template_id INT NOT NULL,
  option_id INT NOT NULL,
  param INT NOT NULL,
  sort_order SMALLINT NOT NULL DEFAULT 0,
  PRIMARY KEY (item_template_id, option_id),
  KEY idx_item_default_option_option (option_id),
  CONSTRAINT fk_item_default_option_item
    FOREIGN KEY (item_template_id) REFERENCES item_template (id) ON DELETE CASCADE,
  CONSTRAINT fk_item_default_option_template
    FOREIGN KEY (option_id) REFERENCES item_option_template (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Existing rows must preserve their current item_shop_option data.
ALTER TABLE item_shop
  ADD COLUMN IF NOT EXISTS option_mode TINYINT NOT NULL DEFAULT 1 AFTER icon_spec;

-- New rows use the shared preset. Existing values remain 1 (CUSTOM).
ALTER TABLE item_shop
  MODIFY COLUMN option_mode TINYINT NOT NULL DEFAULT 0;

-- Import only unambiguous, non-empty shop option sets. Conflicting item IDs
-- are intentionally left for an administrator to resolve in the admin tab.
INSERT IGNORE INTO item_default_option (item_template_id, option_id, param, sort_order)
SELECT s.temp_id, o.option_id, o.param, MIN(o.id)
FROM item_shop s
JOIN item_shop_option o ON o.item_shop_id = s.id
JOIN (
  SELECT temp_id
  FROM (
    SELECT s2.temp_id, s2.id,
           COALESCE(GROUP_CONCAT(CONCAT(o2.option_id, ':', o2.param)
                    ORDER BY o2.option_id, o2.param SEPARATOR ','), '') AS option_signature
    FROM item_shop s2
    LEFT JOIN item_shop_option o2 ON o2.item_shop_id = s2.id
    GROUP BY s2.temp_id, s2.id
  ) variants
  GROUP BY temp_id
  HAVING COUNT(DISTINCT option_signature) = 1
     AND MAX(option_signature) <> ''
) unambiguous ON unambiguous.temp_id = s.temp_id
GROUP BY s.temp_id, o.option_id, o.param;
