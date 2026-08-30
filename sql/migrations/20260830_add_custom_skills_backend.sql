-- Backend-only data for the four planned techniques.  Icons and books are
-- intentionally left as placeholders for the later client/assets step.
-- The migration is idempotent and keeps all Vietnamese text in UTF-8.

START TRANSACTION;

-- Hellzone Grenade: Namek, special targeted skill (template 28).
INSERT INTO `skill_template`
    (`nclass_id`, `id`, `NAME`, `max_point`, `mana_use_type`, `TYPE`, `icon_id`, `dam_info`, `slot`, `skills`)
VALUES
(1, 28, 'Hellzone Grenade', 7, 1, 4, 14642, 'Tổng sát thương: #% - tối đa # mục tiêu', 9,
'[{"power_require":50000000,"damage":200,"dx":280,"dy":80,"price":5000,"max_fight":2,"mana_use":12,"cool_down":26000,"id":193,"point":1,"info":"Hellzone Grenade cấp 1"},{"power_require":100000000,"damage":240,"dx":310,"dy":90,"price":10000,"max_fight":2,"mana_use":12,"cool_down":24500,"id":194,"point":2,"info":"Hellzone Grenade cấp 2"},{"power_require":250000000,"damage":280,"dx":340,"dy":100,"price":16000,"max_fight":3,"mana_use":11,"cool_down":23000,"id":195,"point":3,"info":"Hellzone Grenade cấp 3"},{"power_require":500000000,"damage":320,"dx":370,"dy":110,"price":24000,"max_fight":3,"mana_use":11,"cool_down":21500,"id":196,"point":4,"info":"Hellzone Grenade cấp 4"},{"power_require":900000000,"damage":360,"dx":400,"dy":120,"price":28000,"max_fight":4,"mana_use":10,"cool_down":20000,"id":197,"point":5,"info":"Hellzone Grenade cấp 5"},{"power_require":1500000000,"damage":400,"dx":430,"dy":130,"price":30000,"max_fight":4,"mana_use":10,"cool_down":18500,"id":198,"point":6,"info":"Hellzone Grenade cấp 6"},{"power_require":2500000000,"damage":440,"dx":460,"dy":140,"price":32000,"max_fight":5,"mana_use":9,"cool_down":17000,"id":199,"point":7,"info":"Hellzone Grenade cấp 7"}]')
ON DUPLICATE KEY UPDATE
    `NAME`=VALUES(`NAME`), `max_point`=VALUES(`max_point`), `mana_use_type`=VALUES(`mana_use_type`),
    `TYPE`=VALUES(`TYPE`), `icon_id`=VALUES(`icon_id`), `dam_info`=VALUES(`dam_info`),
    `slot`=VALUES(`slot`), `skills`=VALUES(`skills`);

-- Barrier Prison: Earth, targeted movement boundary with periodic damage.
INSERT INTO `skill_template`
    (`nclass_id`, `id`, `NAME`, `max_point`, `mana_use_type`, `TYPE`, `icon_id`, `dam_info`, `slot`, `skills`)
VALUES
(0, 29, 'Barrier Prison', 7, 1, 1, 3784, 'Sát thương mỗi nhịp: #% - thời gian # ms', 10,
'[{"power_require":50000000,"damage":25,"dx":180,"dy":2500,"price":5000,"max_fight":1,"mana_use":10,"cool_down":30000,"id":200,"point":1,"info":"Barrier Prison cấp 1"},{"power_require":100000000,"damage":30,"dx":200,"dy":3000,"price":10000,"max_fight":1,"mana_use":10,"cool_down":28500,"id":201,"point":2,"info":"Barrier Prison cấp 2"},{"power_require":250000000,"damage":35,"dx":220,"dy":3500,"price":16000,"max_fight":1,"mana_use":9,"cool_down":27000,"id":202,"point":3,"info":"Barrier Prison cấp 3"},{"power_require":500000000,"damage":40,"dx":240,"dy":4000,"price":24000,"max_fight":1,"mana_use":9,"cool_down":25500,"id":203,"point":4,"info":"Barrier Prison cấp 4"},{"power_require":900000000,"damage":45,"dx":260,"dy":4500,"price":28000,"max_fight":1,"mana_use":8,"cool_down":24000,"id":204,"point":5,"info":"Barrier Prison cấp 5"},{"power_require":1500000000,"damage":50,"dx":280,"dy":5000,"price":30000,"max_fight":1,"mana_use":8,"cool_down":22500,"id":205,"point":6,"info":"Barrier Prison cấp 6"},{"power_require":2500000000,"damage":55,"dx":300,"dy":5500,"price":32000,"max_fight":1,"mana_use":7,"cool_down":21000,"id":206,"point":7,"info":"Barrier Prison cấp 7"}]')
ON DUPLICATE KEY UPDATE
    `NAME`=VALUES(`NAME`), `max_point`=VALUES(`max_point`), `mana_use_type`=VALUES(`mana_use_type`),
    `TYPE`=VALUES(`TYPE`), `icon_id`=VALUES(`icon_id`), `dam_info`=VALUES(`dam_info`),
    `slot`=VALUES(`slot`), `skills`=VALUES(`skills`);

-- Energy Absorption: one identical template per race so it is learnable by all.
INSERT INTO `skill_template`
    (`nclass_id`, `id`, `max_point`, `mana_use_type`, `TYPE`, `icon_id`, `NAME`, `dam_info`, `slot`, `skills`)
SELECT classes.nclass_id, 30, 7, 1, 3, 720, 'Energy Absorption',
       'Hấp thụ #% sát thương năng lượng - thời gian # ms',
       classes.slot,
       '[{"power_require":50000000,"damage":20,"dx":15,"dy":3000,"price":5000,"max_fight":1,"mana_use":8,"cool_down":45000,"id":207,"point":1,"info":"Energy Absorption cấp 1"},{"power_require":100000000,"damage":25,"dx":20,"dy":3500,"price":10000,"max_fight":1,"mana_use":8,"cool_down":42500,"id":208,"point":2,"info":"Energy Absorption cấp 2"},{"power_require":250000000,"damage":30,"dx":25,"dy":4000,"price":16000,"max_fight":2,"mana_use":7,"cool_down":40000,"id":209,"point":3,"info":"Energy Absorption cấp 3"},{"power_require":500000000,"damage":35,"dx":30,"dy":4500,"price":24000,"max_fight":2,"mana_use":7,"cool_down":37500,"id":210,"point":4,"info":"Energy Absorption cấp 4"},{"power_require":900000000,"damage":40,"dx":35,"dy":5000,"price":28000,"max_fight":3,"mana_use":6,"cool_down":35000,"id":211,"point":5,"info":"Energy Absorption cấp 5"},{"power_require":1500000000,"damage":45,"dx":40,"dy":5500,"price":30000,"max_fight":3,"mana_use":6,"cool_down":32500,"id":212,"point":6,"info":"Energy Absorption cấp 6"},{"power_require":2500000000,"damage":50,"dx":45,"dy":6000,"price":32000,"max_fight":4,"mana_use":5,"cool_down":30000,"id":213,"point":7,"info":"Energy Absorption cấp 7"}]'
FROM (SELECT 0 AS nclass_id, 11 AS slot UNION ALL SELECT 1, 10 UNION ALL SELECT 2, 10) classes
ON DUPLICATE KEY UPDATE
    `NAME`=VALUES(`NAME`), `max_point`=VALUES(`max_point`), `mana_use_type`=VALUES(`mana_use_type`),
    `TYPE`=VALUES(`TYPE`), `icon_id`=VALUES(`icon_id`), `dam_info`=VALUES(`dam_info`),
    `slot`=VALUES(`slot`), `skills`=VALUES(`skills`);

-- Super Ghost Kamikaze: Xayda, homing special skill.
INSERT INTO `skill_template`
    (`nclass_id`, `id`, `max_point`, `mana_use_type`, `TYPE`, `icon_id`, `NAME`, `dam_info`, `slot`, `skills`)
VALUES
(2, 31, 7, 1, 4, 722, 'Super Ghost Kamikaze', 'Tổng sát thương: #% - triệu hồi # hồn ma', 10,
'[{"power_require":50000000,"damage":180,"dx":280,"dy":70,"price":5000,"max_fight":1,"mana_use":12,"cool_down":38000,"id":214,"point":1,"info":"Super Ghost Kamikaze cấp 1"},{"power_require":100000000,"damage":220,"dx":310,"dy":75,"price":10000,"max_fight":2,"mana_use":12,"cool_down":36000,"id":215,"point":2,"info":"Super Ghost Kamikaze cấp 2"},{"power_require":250000000,"damage":260,"dx":340,"dy":80,"price":16000,"max_fight":2,"mana_use":11,"cool_down":34000,"id":216,"point":3,"info":"Super Ghost Kamikaze cấp 3"},{"power_require":500000000,"damage":310,"dx":370,"dy":85,"price":24000,"max_fight":3,"mana_use":11,"cool_down":32000,"id":217,"point":4,"info":"Super Ghost Kamikaze cấp 4"},{"power_require":900000000,"damage":360,"dx":400,"dy":90,"price":28000,"max_fight":3,"mana_use":10,"cool_down":30000,"id":218,"point":5,"info":"Super Ghost Kamikaze cấp 5"},{"power_require":1500000000,"damage":410,"dx":430,"dy":95,"price":30000,"max_fight":4,"mana_use":9,"cool_down":28000,"id":219,"point":6,"info":"Super Ghost Kamikaze cấp 6"},{"power_require":2500000000,"damage":460,"dx":460,"dy":100,"price":32000,"max_fight":5,"mana_use":8,"cool_down":26000,"id":220,"point":7,"info":"Super Ghost Kamikaze cấp 7"}]')
ON DUPLICATE KEY UPDATE
    `NAME`=VALUES(`NAME`), `max_point`=VALUES(`max_point`), `mana_use_type`=VALUES(`mana_use_type`),
    `TYPE`=VALUES(`TYPE`), `icon_id`=VALUES(`icon_id`), `dam_info`=VALUES(`dam_info`),
    `slot`=VALUES(`slot`), `skills`=VALUES(`skills`);

COMMIT;
