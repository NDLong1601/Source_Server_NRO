-- Khí Nguyên Trảm belongs to the Earth class (nclass_id = 0).
-- The custom client effect uses skill IDs 186..192, which must remain unique.
-- Icon 14658 is dedicated to the skill-tab artwork and avoids stale client caches.

START TRANSACTION;

INSERT INTO `skill_template`
    (`nclass_id`, `id`, `NAME`, `max_point`, `mana_use_type`, `TYPE`, `icon_id`, `dam_info`, `slot`, `skills`)
VALUES
    (0, 27, 'Khí Nguyên Trảm', 7, 0, 4, 14658,
     'Sát thương: #% - xuyên tối đa 3 mục tiêu', 9,
     '[{"power_require":50000000,"damage":240,"dx":250,"dy":70,"price":5000,"max_fight":3,"mana_use":3500,"cool_down":9000,"id":186,"point":1,"info":"Khí Nguyên Trảm cấp 1"},{"power_require":100000000,"damage":270,"dx":270,"dy":75,"price":10000,"max_fight":3,"mana_use":4500,"cool_down":8500,"id":187,"point":2,"info":"Khí Nguyên Trảm cấp 2"},{"power_require":250000000,"damage":300,"dx":290,"dy":80,"price":16000,"max_fight":3,"mana_use":5500,"cool_down":8000,"id":188,"point":3,"info":"Khí Nguyên Trảm cấp 3"},{"power_require":500000000,"damage":330,"dx":310,"dy":85,"price":24000,"max_fight":3,"mana_use":6500,"cool_down":7500,"id":189,"point":4,"info":"Khí Nguyên Trảm cấp 4"},{"power_require":900000000,"damage":360,"dx":330,"dy":90,"price":28000,"max_fight":3,"mana_use":7500,"cool_down":7000,"id":190,"point":5,"info":"Khí Nguyên Trảm cấp 5"},{"power_require":1500000000,"damage":390,"dx":350,"dy":100,"price":30000,"max_fight":3,"mana_use":9000,"cool_down":6500,"id":191,"point":6,"info":"Khí Nguyên Trảm cấp 6"},{"power_require":2500000000,"damage":420,"dx":370,"dy":110,"price":32000,"max_fight":3,"mana_use":11000,"cool_down":6000,"id":192,"point":7,"info":"Khí Nguyên Trảm cấp 7"}]')
ON DUPLICATE KEY UPDATE
    `NAME` = VALUES(`NAME`),
    `max_point` = VALUES(`max_point`),
    `mana_use_type` = VALUES(`mana_use_type`),
    `TYPE` = VALUES(`TYPE`),
    `icon_id` = VALUES(`icon_id`),
    `dam_info` = VALUES(`dam_info`),
    `slot` = VALUES(`slot`),
    `skills` = VALUES(`skills`);

COMMIT;
