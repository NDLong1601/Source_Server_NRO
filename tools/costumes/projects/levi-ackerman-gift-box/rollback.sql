-- Use only before any player has received item 2247 or 2248.
START TRANSACTION;
DELETE FROM gift_box_config WHERE box_template_id = 2248;
DELETE FROM giftcode WHERE code = 'LEVIA7Q9T2';
DELETE FROM item_default_option WHERE item_template_id IN (2247, 2248);
DELETE FROM head_avatar WHERE head_id = 2198;
DELETE FROM part WHERE id IN (2198, 2199, 2200);
DELETE FROM item_template WHERE id IN (2247, 2248);
COMMIT;
