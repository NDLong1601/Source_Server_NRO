-- Remove only the rows installed for Cải trang Mitsuri Kanroji.
START TRANSACTION;

DELETE FROM item_shop_option WHERE item_shop_id IN
    (SELECT id FROM item_shop WHERE tab_id = 1120 AND temp_id = 2069);
DELETE FROM item_shop WHERE tab_id = 1120 AND temp_id = 2069;
DELETE FROM item_default_option WHERE item_template_id = 2069;
DELETE FROM head_avatar WHERE head_id = 2156 AND avatar_id = 26017;
DELETE FROM item_template WHERE id = 2069
    AND head = 2156 AND body = 2157 AND leg = 2158;
DELETE FROM part WHERE id IN (2156, 2157, 2158);

COMMIT;
