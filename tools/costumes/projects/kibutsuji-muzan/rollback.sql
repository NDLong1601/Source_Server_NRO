-- Remove only the rows installed for Cải trang Kibutsuji Muzan.
START TRANSACTION;

DELETE FROM item_shop_option WHERE item_shop_id IN
    (SELECT id FROM item_shop WHERE tab_id = 1120 AND temp_id = 2075);
DELETE FROM item_shop WHERE tab_id = 1120 AND temp_id = 2075;
DELETE FROM item_default_option WHERE item_template_id = 2075;
DELETE FROM head_avatar WHERE head_id = 2174 AND avatar_id = 26140;
DELETE FROM item_template WHERE id = 2075
    AND head = 2174 AND body = 2175 AND leg = 2176;
DELETE FROM part WHERE id IN (2174, 2175, 2176);

COMMIT;
