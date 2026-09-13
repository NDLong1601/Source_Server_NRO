-- Remove only the rows installed for Cải trang Kokushibo.
START TRANSACTION;

DELETE FROM item_shop_option WHERE item_shop_id IN
    (SELECT id FROM item_shop WHERE tab_id = 1120 AND temp_id = 2073);
DELETE FROM item_shop WHERE tab_id = 1120 AND temp_id = 2073;
DELETE FROM item_default_option WHERE item_template_id = 2073;
DELETE FROM head_avatar WHERE head_id = 2168 AND avatar_id = 26078;
DELETE FROM item_template WHERE id = 2073
    AND head = 2168 AND body = 2169 AND leg = 2170;
DELETE FROM part WHERE id IN (2168, 2169, 2170);

COMMIT;
