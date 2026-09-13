-- Remove only the Tokitou Muichirou rows created by install.sql.

START TRANSACTION;

DELETE FROM item_shop_option
WHERE item_shop_id IN (
    SELECT id FROM item_shop WHERE tab_id = 1120 AND temp_id = 2063
);
DELETE FROM item_shop
WHERE tab_id = 1120 AND temp_id = 2063;

DELETE FROM item_default_option
WHERE item_template_id = 2063;
DELETE FROM head_avatar
WHERE head_id = 2138 AND avatar_id = 25042;
DELETE FROM item_template
WHERE id = 2063
  AND head = 2138 AND body = 2139 AND leg = 2140;
DELETE FROM part
WHERE id IN (2138, 2139, 2140);

COMMIT;
