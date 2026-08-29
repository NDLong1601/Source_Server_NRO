-- Bản cấu hình option đang triển khai dở, được tách khỏi cải trang Giyuu item 2219
-- trước khi item bị gỡ. Không chạy file này khi item_template 2219 chưa tồn tại vì
-- bảng item_default_option có khóa ngoại tới item_template(id).
SET NAMES utf8mb4;
START TRANSACTION;

INSERT INTO item_default_option (item_template_id, option_id, param, sort_order)
SELECT 2219, option_id, param, sort_order
FROM (
    SELECT 50 AS option_id, 30 AS param, 0 AS sort_order
    UNION ALL SELECT 77, 30, 1
    UNION ALL SELECT 103, 30, 2
    UNION ALL SELECT 5, 30, 3
    UNION ALL SELECT 10, 30, 4
    UNION ALL SELECT 108, 20, 5
    UNION ALL SELECT 30, 0, 6
) AS draft
WHERE EXISTS (SELECT 1 FROM item_template WHERE id = 2219)
ON DUPLICATE KEY UPDATE
    param = VALUES(param),
    sort_order = VALUES(sort_order);

COMMIT;
