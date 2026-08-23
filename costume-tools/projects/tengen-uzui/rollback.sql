-- Remove only the rows installed for Cải trang Tengen Uzui.
START TRANSACTION;

DELETE FROM item_shop_option WHERE item_shop_id IN
    (SELECT id FROM item_shop WHERE tab_id = 1120 AND temp_id = 2067);
DELETE FROM item_shop WHERE tab_id = 1120 AND temp_id = 2067;
DELETE FROM item_default_option WHERE item_template_id = 2067;
DELETE FROM head_avatar WHERE head_id = 2150 AND avatar_id = 26015;
DELETE FROM item_template WHERE id = 2067
    AND head = 2150 AND body = 2151 AND leg = 2152;
DELETE FROM part WHERE id IN (2150, 2151, 2152);

COMMIT;
