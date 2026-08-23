-- Remove only the rows installed for Cải trang Tomioka Giyuu.
START TRANSACTION;

DELETE FROM item_shop_option WHERE item_shop_id IN
    (SELECT id FROM item_shop WHERE tab_id = 1120 AND temp_id = 2064);
DELETE FROM item_shop WHERE tab_id = 1120 AND temp_id = 2064;
DELETE FROM item_default_option WHERE item_template_id = 2064;
DELETE FROM head_avatar WHERE head_id = 2141 AND avatar_id = 26012;
DELETE FROM item_template WHERE id = 2064
    AND head = 2141 AND body = 2142 AND leg = 2143;
DELETE FROM part WHERE id IN (2141, 2142, 2143);

COMMIT;
