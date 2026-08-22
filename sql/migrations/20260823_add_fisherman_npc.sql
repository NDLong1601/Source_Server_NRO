-- NPC 113: Ngư dân multipart, kích thước cân bằng với NPC client gốc.
-- Icon: Head 25018, Body 25019, Leg 25020, Avatar 25021.
-- Client vẽ slot Head -> Leg -> Body. Ta ánh xạ hình Leg -> Body -> Head vào
-- ba slot tương ứng để phần đầu được vẽ cao nhất mà không sửa client.
CREATE TABLE IF NOT EXISTS admin_npc_render_config (
    npc_id INT NOT NULL PRIMARY KEY,
    layer_order VARCHAR(32) NOT NULL DEFAULT 'NATIVE',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

START TRANSACTION;

INSERT INTO part (id, `TYPE`, `DATA`) VALUES
    (2135, 0, '[[25020,7,14],[0,0,0],[0,0,0]]'),
    (2136, 1, '[[0,0,0],[25018,2,-25],[0,0,0],[0,0,0],[0,0,0],[0,0,0],[0,0,0],[0,0,0],[0,0,0],[0,0,0],[0,0,0],[0,0,0],[0,0,0],[0,0,0],[0,0,0],[0,0,0],[0,0,0]]'),
    (2137, 2, '[[0,0,0],[25019,-2,-19],[0,0,0],[0,0,0],[0,0,0],[0,0,0],[0,0,0],[0,0,0],[0,0,0],[0,0,0],[0,0,0],[0,0,0],[0,0,0],[0,0,0]]')
ON DUPLICATE KEY UPDATE `TYPE` = VALUES(`TYPE`), `DATA` = VALUES(`DATA`);

INSERT INTO npc_template (id, `NAME`, head, body, leg, avatar)
VALUES (113, 'Ngư dân', 2135, 2136, 2137, 25021)
ON DUPLICATE KEY UPDATE
    `NAME` = VALUES(`NAME`), head = VALUES(head), body = VALUES(body),
    leg = VALUES(leg), avatar = VALUES(avatar);

INSERT INTO admin_npc_render_config (npc_id, layer_order)
VALUES (113, 'LEG_BODY_HEAD')
ON DUPLICATE KEY UPDATE layer_order = VALUES(layer_order);

-- Chỉ nối tuple khi map 5 chưa có NPC 113; không đụng các NPC hiện hữu.
UPDATE map_template
SET npcs = CASE
    WHEN npcs IS NULL OR TRIM(npcs) = '' OR TRIM(npcs) = '[]' THEN '[[113,328,288]]'
    ELSE CONCAT(LEFT(TRIM(npcs), CHAR_LENGTH(TRIM(npcs)) - 1), ',[113,328,288]]')
END
WHERE id = 5 AND npcs NOT REGEXP '\\[[[:space:]]*113[[:space:]]*,';

COMMIT;
