-- Đồng bộ danh hiệu với idEffect và sửa các mô tả nhiệm vụ không khớp trigger.
-- Có thể chạy lại an toàn trên database đã có dữ liệu.
START TRANSACTION;

INSERT INTO `data_badges` (`id`, `idEffect`, `idItem`, `NAME`, `Options`) VALUES
    (10, 215, 1286, 'Kẻ thao túng sói', '[{"param":30,"id":93}]'),
    (11, 216, 1287, 'Nước anh bao', '[{"param":30,"id":93}]'),
    (19, 229, 1300, 'Thánh ở dơ', '[{"param":30,"id":93}]')
ON DUPLICATE KEY UPDATE
    `idEffect` = VALUES(`idEffect`),
    `idItem` = VALUES(`idItem`),
    `NAME` = VALUES(`NAME`),
    `Options` = VALUES(`Options`);

UPDATE `task_badges_template`
SET `NAME` = CASE `id`
        WHEN 6 THEN 'Hoàn thành 10 nhiệm vụ địa ngục tại Bò Mộng'
        WHEN 7 THEN 'Đánh bại hoặc cho Sói Hẹc Quyn ăn xương 20 lần'
        WHEN 8 THEN 'Đánh bại Xinbatô Nước 5 lần'
        WHEN 11 THEN 'Tiêu diệt 30 Boss Ở Dơ'
    END,
    `idBadgesReward` = CASE `id`
        WHEN 7 THEN 215
        WHEN 8 THEN 216
        WHEN 11 THEN 229
        ELSE `idBadgesReward`
    END
WHERE `id` IN (6, 7, 8, 11);

COMMIT;
