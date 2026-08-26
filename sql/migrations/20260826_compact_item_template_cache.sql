-- Keep all item templates inside the legacy client's two 16-bit update packets.
-- Names remain unchanged. Descriptions below are intentionally concise because
-- the client cache sends every template name and description at login.

START TRANSACTION;

UPDATE item_template
SET description = CASE
    WHEN id = 2083 THEN 'Cá 1-4'
    WHEN id = 2084 THEN 'Cá 1-7'
    WHEN id = 2085 THEN 'Cá 1-10'
    WHEN id = 2086 THEN 'Cá 1-13'
    WHEN id = 2087 THEN 'Cá 1-15'
    WHEN id = 2088 THEN 'Cá 1-4'
    WHEN id = 2089 THEN 'Cá 1-6'
    WHEN id = 2090 THEN 'Cá 1-8'
    WHEN id = 2091 THEN 'Cá 2-10'
    WHEN id = 2092 THEN 'Cá 3-12'
    WHEN id = 2093 THEN 'Cá 5-14'
    WHEN id = 2094 THEN 'Cá 7-15'
    WHEN id BETWEEN 2095 AND 2114 THEN 'Đồ câu'
    WHEN id = 2115 THEN 'Xu câu'
    WHEN id BETWEEN 2116 AND 2123 THEN 'Rác'
    WHEN id BETWEEN 2124 AND 2138 THEN CONCAT('Cấp ', id - 2123)
    WHEN id BETWEEN 2139 AND 2153 THEN 'x3 Xu'
    ELSE description
END
WHERE id BETWEEN 2083 AND 2153;

-- The aura name already conveys its type and level; omit the repeated text.
UPDATE item_template
SET description = ''
WHERE id BETWEEN 2154 AND 2217
  AND NAME LIKE 'Hào Quang %'
  AND description = 'Hào quang.';

COMMIT;
