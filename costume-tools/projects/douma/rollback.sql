-- Remove only the rows installed for Cải trang Douma.
START TRANSACTION;

DELETE FROM item_shop_option WHERE item_shop_id IN
    (SELECT id FROM item_shop WHERE tab_id = 1120 AND temp_id = 2072);
DELETE FROM item_shop WHERE tab_id = 1120 AND temp_id = 2072;
DELETE FROM item_default_option WHERE item_template_id = 2072;
DELETE FROM head_avatar WHERE head_id = 2165 AND avatar_id = 26058;
DELETE FROM item_template WHERE id = 2072
    AND head = 2165 AND body = 2166 AND leg = 2167;
DELETE FROM part WHERE id IN (2165, 2166, 2167);

COMMIT;
