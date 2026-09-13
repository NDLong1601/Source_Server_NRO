-- Remove only the rows installed for Cải trang Rengoku.
START TRANSACTION;

DELETE FROM item_shop_option WHERE item_shop_id IN
    (SELECT id FROM item_shop WHERE tab_id = 1120 AND temp_id = 2066);
DELETE FROM item_shop WHERE tab_id = 1120 AND temp_id = 2066;
DELETE FROM item_default_option WHERE item_template_id = 2066;
DELETE FROM head_avatar WHERE head_id = 2147 AND avatar_id = 26014;
DELETE FROM item_template WHERE id = 2066
    AND head = 2147 AND body = 2148 AND leg = 2149;
DELETE FROM part WHERE id IN (2147, 2148, 2149);

COMMIT;
