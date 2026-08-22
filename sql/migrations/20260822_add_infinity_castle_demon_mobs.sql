-- Installs the nine Infinity Castle demon mobs and replaces map 187-190 spawns.
-- Mob 110 and 111 are intentionally reused: the old Snowman/Yeti templates had
-- no runtime payload, map spawn, radar entry, admin mob config or event-item
-- reference. Reusing 111 also keeps the updateMap mob count below the signed
-- byte boundary; 128 templates make this client abort the map-data packet.

START TRANSACTION;

-- Clean up the first deployment, where Gyokko used ID 127 and raised the
-- template count to 128. The name guard preserves an unrelated future ID 127.
DELETE FROM `mob_template`
WHERE `id` = 127 AND `NAME` = 'Thượng Huyền Gyokko';

INSERT INTO `mob_template`
    (`id`, `TYPE`, `NAME`, `hp`, `range_move`, `speed`, `dart_Type`, `percent_dame`, `percent_tiem_nang`)
VALUES
    (110, 4, 'Quỷ Đơn Cánh', 1500000, 33, 2, 47, 5, 10),
    (111, 4, 'Gyokko', 8000000, 33, 2, 47, 5, 10),
    (120, 1, 'Daki', 8000000, 33, 2, 47, 5, 10),
    (121, 1, 'Gyutaro', 10000000, 33, 2, 47, 5, 10),
    (122, 1, 'Quỷ Tăng Huyết Quy', 1000000, 33, 2, 47, 5, 10),
    (123, 1, 'Quỷ Đầm lầy', 1000000, 33, 2, 47, 5, 10),
    (124, 1, 'Nakime', 5000000, 33, 2, 47, 5, 10),
    (125, 1, 'Yahaba', 800000, 33, 2, 47, 5, 10),
    (126, 1, 'Kaigaku', 3000000, 33, 2, 47, 5, 10)
ON DUPLICATE KEY UPDATE
    `TYPE` = VALUES(`TYPE`),
    `NAME` = VALUES(`NAME`),
    `hp` = VALUES(`hp`),
    `range_move` = VALUES(`range_move`),
    `speed` = VALUES(`speed`),
    `dart_Type` = VALUES(`dart_Type`),
    `percent_dame` = VALUES(`percent_dame`),
    `percent_tiem_nang` = VALUES(`percent_tiem_nang`);

UPDATE `map_template`
SET `mobs` = '[[119,12,600000,204,360],[119,12,600000,612,360],[119,12,600000,486,192],[119,12,600000,690,192],[122,12,1000000,300,360],[122,12,1000000,600,192],[123,12,1000000,516,360],[123,12,1000000,780,192],[124,12,5000000,390,192],[125,12,800000,732,360],[125,12,800000,876,360],[111,12,8000000,240,96],[111,12,8000000,720,96],[110,12,1500000,390,96],[110,12,1500000,780,96]]'
WHERE `id` = 187;

UPDATE `map_template`
SET `mobs` = '[[121,14,10000000,780,192],[121,14,10000000,240,528],[122,14,1000000,980,192],[122,14,1000000,540,528],[123,14,1000000,150,336],[123,14,1000000,220,672],[124,14,5000000,620,672],[125,14,800000,330,336],[125,14,800000,840,528],[126,14,3000000,470,336],[126,14,3000000,1020,672],[120,14,8000000,1160,192],[120,14,8000000,1080,528],[111,14,8000000,600,120],[111,14,8000000,660,288],[110,14,1500000,900,408],[110,14,1500000,420,600]]'
WHERE `id` = 188;

UPDATE `map_template`
SET `mobs` = '[[121,16,10000000,180,192],[122,16,1000000,360,192],[125,16,800000,540,192],[126,16,3000000,1020,192],[110,16,1500000,1260,192],[121,16,10000000,360,456],[122,16,1000000,720,456],[111,16,8000000,1080,456],[111,16,8000000,180,600],[125,16,800000,420,600],[124,16,5000000,600,624],[126,16,3000000,780,624],[110,16,1500000,1080,600],[110,16,1500000,1320,600],[120,16,8000000,120,336],[120,16,8000000,1320,336],[111,16,8000000,480,552],[120,16,8000000,960,552]]'
WHERE `id` = 189;

UPDATE `map_template`
SET `mobs` = '[[120,18,8000000,480,192],[120,18,8000000,1140,192],[120,18,8000000,180,360],[120,18,8000000,900,432],[120,18,8000000,600,624],[120,18,8000000,1260,600],[124,18,5000000,600,432]]'
WHERE `id` = 190;

COMMIT;
