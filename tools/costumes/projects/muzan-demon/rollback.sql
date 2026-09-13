-- Remove only the rows installed for Cải trang Muzan Demon.
START TRANSACTION;

DELETE FROM item_shop_option WHERE item_shop_id IN
    (SELECT id FROM item_shop WHERE tab_id = 1120 AND temp_id = 2076);
DELETE FROM item_shop WHERE tab_id = 1120 AND temp_id = 2076;
DELETE FROM item_default_option WHERE item_template_id = 2076;
DELETE FROM head_avatar WHERE head_id = 2177 AND avatar_id = 26160;
DELETE FROM item_template WHERE id = 2076
    AND head = 2177 AND body = 2178 AND leg = 2179;
DELETE FROM part WHERE id IN (2177, 2178, 2179);

COMMIT;
