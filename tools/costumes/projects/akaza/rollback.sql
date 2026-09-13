-- Remove only the rows installed for Cải trang Akaza.
START TRANSACTION;

DELETE FROM item_shop_option WHERE item_shop_id IN
    (SELECT id FROM item_shop WHERE tab_id = 1120 AND temp_id = 2071);
DELETE FROM item_shop WHERE tab_id = 1120 AND temp_id = 2071;
DELETE FROM item_default_option WHERE item_template_id = 2071;
DELETE FROM head_avatar WHERE head_id = 2162 AND avatar_id = 26038;
DELETE FROM item_template WHERE id = 2071
    AND head = 2162 AND body = 2163 AND leg = 2164;
DELETE FROM part WHERE id IN (2162, 2163, 2164);

COMMIT;
