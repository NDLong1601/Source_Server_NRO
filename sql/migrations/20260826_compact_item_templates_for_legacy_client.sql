-- Keep all current item templates while fitting command -28 into the two
-- packets supported reliably by the original client (reload + append).
-- Fishing items 2083..2153 retain their detailed functional descriptions.

START TRANSACTION;

UPDATE item_template
SET description = 'Đủ 4 mảnh để ghép Rương Hủy Diệt.'
WHERE id BETWEEN 2027 AND 2046;

UPDATE item_template
SET description = 'Cải trang.'
WHERE id BETWEEN 2062 AND 2082;

UPDATE item_template
SET description = 'Hào quang.'
WHERE id BETWEEN 2154 AND 2217;

COMMIT;
