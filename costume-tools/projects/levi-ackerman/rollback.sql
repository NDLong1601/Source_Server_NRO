-- Remove only the rows installed for Cải trang Levi Ackerman; no item_shop row was created.
START TRANSACTION;

DELETE FROM item_default_option WHERE item_template_id = 2247;
DELETE FROM head_avatar WHERE head_id = 2198 AND avatar_id = 26574;
DELETE FROM item_template WHERE id = 2247
    AND head = 2198 AND body = 2199 AND leg = 2200;
DELETE FROM part WHERE id IN (2198, 2199, 2200);

COMMIT;
