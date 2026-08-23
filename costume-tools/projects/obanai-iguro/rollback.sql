-- Remove only the rows installed for Cải trang Obanai Iguro.
START TRANSACTION;

DELETE FROM item_shop_option WHERE item_shop_id IN
    (SELECT id FROM item_shop WHERE tab_id = 1120 AND temp_id = 2065);
DELETE FROM item_shop WHERE tab_id = 1120 AND temp_id = 2065;
DELETE FROM item_default_option WHERE item_template_id = 2065;
DELETE FROM head_avatar WHERE head_id = 2144 AND avatar_id = 26013;
DELETE FROM item_template WHERE id = 2065
    AND head = 2144 AND body = 2145 AND leg = 2146;
DELETE FROM part WHERE id IN (2144, 2145, 2146);

COMMIT;
