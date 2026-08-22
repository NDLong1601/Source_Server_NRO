START TRANSACTION;

INSERT INTO `mob_template`
    (`id`, `TYPE`, `NAME`, `hp`, `range_move`, `speed`, `dart_Type`, `percent_dame`, `percent_tiem_nang`)
VALUES
    (119, 1, 'Quỷ Mạng Nhện', 600000, 33, 2, 47, 5, 10)
ON DUPLICATE KEY UPDATE
    `TYPE` = VALUES(`TYPE`),
    `NAME` = VALUES(`NAME`),
    `hp` = VALUES(`hp`),
    `range_move` = VALUES(`range_move`),
    `speed` = VALUES(`speed`),
    `dart_Type` = VALUES(`dart_Type`),
    `percent_dame` = VALUES(`percent_dame`),
    `percent_tiem_nang` = VALUES(`percent_tiem_nang`);

COMMIT;
