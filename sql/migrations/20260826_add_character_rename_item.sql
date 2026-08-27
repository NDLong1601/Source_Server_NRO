-- Character rename card.
-- Requires the prior aura-item migration, which reserves item_template IDs
-- through 2217. This migration reserves ID 2218 and reuses the existing
-- four-scale icon 2989 (the disciple naming card icon).
--
-- Stop the server and make a database backup before applying this migration.

-- Explicit UTF-8 collation is required for Vietnamese diacritics and Han
-- characters in player.name. VARCHAR(20) counts characters, not UTF-8 bytes.
ALTER TABLE player
    MODIFY name VARCHAR(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL;

START TRANSACTION;

-- Do not overwrite a custom item if this ID was already allocated. The
-- precondition protects the contiguous item-template contract required by the
-- client: item IDs must already end at 2217 before adding ID 2218.
INSERT INTO item_template
    (id, `TYPE`, gender, `NAME`, description, level, icon_id, part,
     is_up_to_up, power_require, gold, gem, head, body, leg)
SELECT 2218, 29, 3, 'Thẻ đổi tên',
       'Sử dụng để đổi tên nhân vật. Hỗ trợ chữ Việt, chữ Hoa và dấu cách.',
       1, 2989, -1, 1, 0, 0, 0, -1, -1, -1
WHERE (SELECT COALESCE(MAX(id), -1) FROM item_template) = 2217;

COMMIT;
