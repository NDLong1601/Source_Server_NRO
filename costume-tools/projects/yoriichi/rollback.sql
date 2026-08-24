-- Remove only the rows installed for Cải trang Yoriichi.
START TRANSACTION;

DELETE FROM item_shop_option WHERE item_shop_id IN
    (SELECT id FROM item_shop WHERE tab_id = 1120 AND temp_id = 2074);
DELETE FROM item_shop WHERE tab_id = 1120 AND temp_id = 2074;
DELETE FROM item_default_option WHERE item_template_id = 2074;
DELETE FROM head_avatar WHERE head_id = 2171 AND avatar_id = 26120;
DELETE FROM item_template WHERE id = 2074
    AND head = 2171 AND body = 2172 AND leg = 2173;
DELETE FROM part WHERE id IN (2171, 2172, 2173);

COMMIT;
