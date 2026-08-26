-- Fix the fishing item-template append packet for the legacy client.
-- The original descriptions push command -28 above the unsigned 16-bit limit.

START TRANSACTION;

UPDATE item_template
SET description = CASE
    WHEN id BETWEEN 2083 AND 2087 THEN CONCAT('Cần câu cấp ', id - 2082, '.')
    WHEN id BETWEEN 2088 AND 2094 THEN CONCAT('Mồi câu cấp ', id - 2087, '.')
    WHEN id BETWEEN 2095 AND 2106 THEN 'Trang bị câu cá.'
    WHEN id BETWEEN 2107 AND 2114 THEN 'Vật phẩm câu cá.'
    WHEN id = 2115 THEN 'Xu sự kiện câu cá.'
    WHEN id BETWEEN 2116 AND 2123 THEN 'Vật phẩm từ biển.'
    WHEN id BETWEEN 2124 AND 2138 THEN CONCAT('Cá cấp ', id - 2123, '.')
    WHEN id BETWEEN 2139 AND 2153 THEN 'Cá khổng lồ, đổi x3 Xu.'
    ELSE description
END
WHERE id BETWEEN 2083 AND 2153;

COMMIT;
