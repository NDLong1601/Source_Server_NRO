-- Remove only the rows installed for Cải trang Shinobu Kocho.
START TRANSACTION;

DELETE FROM item_shop_option WHERE item_shop_id IN
    (SELECT id FROM item_shop WHERE tab_id = 1120 AND temp_id = 2070);
DELETE FROM item_shop WHERE tab_id = 1120 AND temp_id = 2070;
DELETE FROM item_default_option WHERE item_template_id = 2070;
DELETE FROM head_avatar WHERE head_id = 2159 AND avatar_id = 26018;
DELETE FROM item_template WHERE id = 2070
    AND head = 2159 AND body = 2160 AND leg = 2161;
DELETE FROM part WHERE id IN (2159, 2160, 2161);

COMMIT;
