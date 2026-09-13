-- Remove only the rows installed for Cải trang Gyomei Himejima.
START TRANSACTION;

DELETE FROM item_shop_option WHERE item_shop_id IN
    (SELECT id FROM item_shop WHERE tab_id = 1120 AND temp_id = 2068);
DELETE FROM item_shop WHERE tab_id = 1120 AND temp_id = 2068;
DELETE FROM item_default_option WHERE item_template_id = 2068;
DELETE FROM head_avatar WHERE head_id = 2153 AND avatar_id = 26016;
DELETE FROM item_template WHERE id = 2068
    AND head = 2153 AND body = 2154 AND leg = 2155;
DELETE FROM part WHERE id IN (2153, 2154, 2155);

COMMIT;
