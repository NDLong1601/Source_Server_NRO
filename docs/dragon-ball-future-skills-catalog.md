# Danh mục skill Dragon Ball có thể phát triển trong tương lai

Tài liệu này chuyển toàn bộ danh sách skill Dragon Ball đã cung cấp thành một catalog thiết kế cho source Teamobi NRO. Mục tiêu là dùng làm backlog cho server, client VFX, NPC/boss và hệ thống sách học skill.

## 1. Phạm vi và quy ước

- Tên skill được giữ theo danh sách gốc; những tên tiếng Anh có tên Việt tương đương được ghi cùng một dòng.
- Một skill xuất hiện ở nhiều arc vẫn được giữ trong từng arc vì ngữ cảnh, animation và cấp độ sức mạnh có thể khác nhau.
- `Đã có` nghĩa source hiện tại đã có template/cơ chế tương ứng, không có nghĩa đã có đúng animation của biến thể anime.
- `Mở rộng` nghĩa có thể tận dụng `SkillTemplate`, `SkillService`, `NPoint`, trạng thái `EffectSkill` hoặc scheduler hiện có.
- `Logic mới` nghĩa cần thêm state, projectile, vùng tác dụng, AI, protocol hoặc công thức riêng.
- `Boss/NPC` nghĩa nên triển khai trước cho boss/NPC; chỉ đưa thành skill người chơi sau khi có giới hạn chống lạm dụng.

## 2. Nền tảng hiện có trong source

| Nhóm nền tảng | Thành phần hiện tại | Có thể tái sử dụng cho |
|---|---|---|
| Đòn cận chiến | `TYPE=1`, damage theo `NPoint` | Đấm, rush, sword combo, tail attack, physical finisher |
| Chưởng đạn/tia | `TYPE=1` hoặc `TYPE=4`, `dx/dy`, target packet | Kamehameha, Masenko, Death Beam, Galick Gun, Eye Laser |
| Tuyệt kỹ định vị | `TYPE=4`, skill snapshot và scheduler | Hellzone, Spirit Bomb, Death Ball, Planet Crusher |
| Buff/tự dùng | `TYPE=3`, `EffectSkill` | Kaioken, biến hình, absorption, barrier, UI mode |
| Khống chế | trạng thái stun, sleep, bind, Socola, prison | Paralysis, Time Prison, Stone Spit, Galactic Donut |
| Vùng tác dụng | quét mục tiêu theo bán kính/zone | Explosive Wave, Kikoho, Human Extinction Attack |
| Projectile trễ/homing | scheduler server-authoritative | Khí Nguyên Trảm, Ghost, Vital Point Shot, Death Saucer |
| Mastery | `SkillMasteryService`, level 8–20 | Biến thể cấp cao, giảm cooldown, tăng phạm vi, tăng số mục tiêu |
| Asset delivery | packet dữ liệu skill/icon/effect | atlas x1–x4, icon sách, effect ID và cache version |

Các skill đang có template trong source: Dragon (0), Kamejoko (1), Demon (2), Masenko (3), Galick (4), Antomic (5), Thái Dương Hạ San (6), Trị thương (7), Tái tạo năng lượng (8), Kaioken (9), Quả cầu kênh khí (10), Makankosappo (11), Đẻ trứng (12), Biến khỉ (13), Tự sát (14), Liên hoàn (17), Biến Socola (18), Khiên năng lượng (19), Dịch chuyển tức thời (20), Huýt sáo (21), Thôi miên (22), Trói (23), Super Kamejoko (24), Liên hoàn chưởng (25), Ma phong ba (26), Khí Nguyên Trảm (27), cùng bốn skill mở rộng ID 28–31.

## 3. Mức độ ưu tiên đề xuất

| Mức | Ý nghĩa | Nhóm nên ưu tiên |
|---|---|---|
| P0 | Có thể làm ngay bằng logic hiện tại | Biến thể chưởng, vùng nổ, buff đơn giản, boss beam |
| P1 | Cần state/scheduler nhưng rủi ro vừa | summon, homing projectile, barrier, absorption, multi-hit |
| P2 | Cần protocol/client VFX hoặc AI mới | Time-Skip, portal, copy skill, giant avatar, reality warp |
| P3 | Endgame/boss đặc biệt | Hakai, Planet Possession, Immortality, Third Eye, wish-based forms |

---

## 4. Dragon Ball thời niên thiếu – Pilaf / Red Ribbon / Đại hội võ thuật

| Nhân vật | Skill trong danh sách | Hướng phát triển và khả thi |
|---|---|---|
| Goku nhí | Janken/Oẳn Tù Tì; Kamehameha; Kamehameha uốn cong; Kamehameha từ chân; Afterimage; Afterimage Strike; Crazy Fist; Monkey Fist; Hasshuken bắt chước; Power Pole/Như Ý Bổng; Great Ape | Janken/Crazy Fist/Monkey Fist/Power Pole dùng cận chiến combo P0. Kamehameha dùng template 1 và thêm biến thể đường bay P0–P1. Afterimage cần clone/né theo thời gian P1. Hasshuken và Great Ape là copy/transform P2 vì cần state nhân bản hoặc thay outfit/hitbox. |
| Master Roshi | Kamehameha; MAX Power Kamehameha; Afterimage; Drunken Fist; Sleepy Boy Technique; Hypnosis; Thunder Shock Surprise; Mafuba/Ma Phong Ba | Kamehameha và Mafuba đã có nền tảng. MAX Power cần charge dài và damage burst P0. Drunken Fist là combo có xác suất né P1. Hypnosis/Sleepy Boy tái dùng sleep nhưng cần animation và giới hạn boss P0–P1. Thunder Shock Surprise cần channel/khống chế liên tục P1. |
| Krillin | Kamehameha; Kienzan/Khí Nguyên Trảm; Taiyoken/Thái Dương Hạ San; Afterimage; Double Tsuibikidan; Scatter Bullet/Scatter Shot | Kame/KNT/Taiyoken đã có template tương ứng. Double Tsuibikidan và Scatter Bullet là multi-projectile P0–P1. Afterimage dùng buff né ngắn P1. |
| Yamcha | Wolf Fang Fist/Lang Nha Phong Phong Quyền; Neo Wolf Fang Fist; Kamehameha; Spirit Ball/Sokidan; Afterimage | Wolf Fang là combo áp sát P0. Neo Wolf Fang thêm lao lại mục tiêu/hit cuối P1. Spirit Ball cần projectile điều khiển hoặc homing P1. |
| Tien Shinhan | Dodon Ray/Động Động Ba; Kikoho/Khí Công Pháo; Taiyoken; Multi-Form/Tứ Thân Quyền; Four Witches/Tứ Yêu Quyền; Volleyball Fist; Telepathy | Dodon Ray là beam P0. Kikoho là vùng nổ tự gây hao HP hoặc cooldown dài P0–P1. Multi-Form/Four Witches cần clone/đổi model P2. Volleyball Fist là combo hất tung P1. Telepathy phù hợp NPC/boss utility P1. |
| Chiaotzu | Dodon Ray; Telekinesis; Psychic Paralysis; Self-Destruction | Dodon Ray tái dùng. Telekinesis/Psychic Paralysis dùng control state P1. Self-Destruction tái dùng logic Tự sát hiện tại P0. |
| Tao Pai Pai | Dodon Ray; Super Dodon Wave; Tongue Kill; Pillar Throw | Beam P0. Tongue Kill là execute cận chiến có điều kiện HP P1. Pillar Throw là projectile vật thể, cần packet quỹ đạo và collision P1. |
| King Piccolo | Explosive Demon Wave; Mouth Energy Wave; Eye Beam; Telekinesis; Demon Clan Creation; Regeneration | Wave/beam tái dùng P0. Telekinesis/Regeneration mở rộng state P1. Demon Clan Creation cần summon mob và giới hạn số con P1. |
| Piccolo Jr. | Explosive Demon Wave; Super Explosive Wave; Chasing Bullet; Mouth Beam; Eye Beam; Regeneration; Giant Form | Wave/beam/regeneration dùng nền tảng có sẵn. Chasing Bullet là homing P1. Giant Form cần transform, scale hitbox và outfit P2. |

**Ứng viên nên làm đầu tiên:** Wolf Fang Fist, Spirit Ball, Double Tsuibikidan, Kikoho và Afterimage vì khác biệt rõ nhưng vẫn nằm trong khả năng engine hiện tại.

---

## 5. Saiyan Arc

| Nhân vật | Skill trong danh sách | Hướng phát triển và khả thi |
|---|---|---|
| Goku | Kamehameha; Kaioken; Kaioken x2/x3/x4; Kaioken Kamehameha; Genki Dama/Spirit Bomb; Solar Flare; Afterimage | Kaioken đã có và có thể mở cấp/x4 bằng mastery P0. Kaioken Kamehameha là composite buff + chưởng P1. Spirit Bomb là charge/targeted area P1. Solar Flare/Afterimage dùng control/né P0–P1. |
| Vegeta | Galick Gun; Galick Barrage; Explosive Wave; ki barrage; Power Ball/Mặt Trăng Nhân Tạo; Great Ape; Tail Attack | Galick Gun đã có template Xayda. Barrage cần nhiều projectile P0. Explosive Wave là vùng quanh caster P0. Power Ball/Great Ape là chuỗi transform + summon/ảnh hưởng map P2. |
| Nappa | Bomber DX; Break Cannon/Mouth Blast; Explosive Wave; Arm Break; Giant Storm | Beam và vùng nổ P0. Arm Break là debuff/disable skill P1. Giant Storm là boss area multi-wave P1. |
| Raditz | Double Sunday; Saturday Crush; ki blast barrage | Tất cả là projectile/barrage P0; Double Sunday có thể dùng hai đạn song song. |
| Piccolo | Makankosappo; Explosive Demon Wave; Mouth Blast; Regeneration; Arm Extension | Makankosappo và regeneration đã có nền tảng. Arm Extension cần melee reach/hitbox mới P1. |
| Gohan | Masenko; rage power-up; headbutt/rush attack; Great Ape | Masenko đã có. Rage là buff theo HP hoặc điều kiện chiến đấu P1. Rush P0; Great Ape P2. |
| Krillin | Kienzan; Scatter Energy Wave; Kamehameha | KNT/Kame đã có; Scatter Energy Wave là multi-projectile P0. |
| Tien | Kikoho; Taiyoken | Tái dùng Kikoho/Taiyoken ở trên; khác biệt chủ yếu là damage, charge và VFX. |
| Chiaotzu | Psychic Paralysis; Self-Destruction | Tái dùng control và Tự sát P0–P1. |

**Ghi chú cân bằng:** Galick Gun nên là chưởng tầm xa biểu tượng của Xayda; không nên gộp trực tiếp với Antomic chỉ bằng đổi icon vì cần animation/âm thanh khác.

---

## 6. Namek / Frieza Arc

| Nhân vật | Skill trong danh sách | Hướng phát triển và khả thi |
|---|---|---|
| Goku | Kaioken x10/x20; Kaioken Kamehameha; Spirit Bomb khổng lồ; Kamehameha | Kaioken mở mastery. Composite Kaioken Kamehameha P1. Spirit Bomb cần charge lâu, vùng nổ lớn, chống spam P1. |
| Vegeta | Explosive Wave; ki barrage; Dirty Fireworks; Energy Disk; Galick-style blasts | Wave/barrage P0. Dirty Fireworks cần đánh dấu nhiều mục tiêu rồi nổ đồng thời P1. Energy Disk là projectile cắt/homing, có thể tái dùng KNT trajectory P1. |
| Krillin | Kienzan; Kamehameha; Scatter Shot | KNT/Kame có sẵn; Scatter Shot P0. |
| Gohan | Masenko; Double Masenko; rage attacks | Masenko có sẵn. Double Masenko cần hai projectile và packet spawn P0–P1. Rage dùng buff điều kiện P1. |
| Piccolo | Namek Fusion với Nail; Explosive Demon Wave; Rapid Fire Ki | Fusion là transform/power state P2. Wave có sẵn; Rapid Fire là barrage P0. |
| Frieza | Death Beam; Barrage Death Beam; Eye Laser; Telekinesis; Death Ball; Death Saucer; Crazy Finger Beam; Death Storm; Tail Attack; 100% Full Power | Beam/laser/barrage P0. Death Ball và Death Saucer dùng delayed area/homing P1. Telekinesis cần bind/throw state P1. 100% Full Power là transform P2. |
| Captain Ginyu | Body Change; Milky Cannon; telekinesis | Body Change là P2/P3 vì cần hoán đổi state nhân vật, chỉ số, trang bị và quyền điều khiển an toàn. Milky Cannon là projectile P0. |
| Jeice | Crusher Ball; ki barrage | Projectile/barrage P0. |
| Burter | Mach-speed rush; Purple Comet Attack cùng Jeice | Rush tốc độ cao P1. Purple Comet là skill phối hợp hai boss, cần party/AI coordination P2. |
| Recoome | Recoome Eraser Gun; Recoome Ultra Fighting Bomber; Recoome Kick | Beam, area bomber và melee P0–P1. |
| Guldo | Time Freeze; Telekinesis; Psychic Paralysis | Control thời gian là P2; telekinesis/paralysis P1. Cần giới hạn boss resistance và server timeout. |
| Zarbon | Monster Transformation; Elegant Blaster | Transformation P1–P2; Elegant Blaster là beam P0. |
| Dodoria | Mouth Energy Wave; Dodoria Beam | Beam P0. |

**Ứng viên boss nổi bật:** Death Beam, Death Saucer, Dirty Fireworks, Time Freeze và Body Change. Trong đó Body Change chỉ nên thử nghiệm ở boss instance trước.

---

## 7. Android / Cell Arc

| Nhân vật | Skill trong danh sách | Hướng phát triển và khả thi |
|---|---|---|
| Vegeta | Big Bang Attack; Final Flash; Galick Gun; ki barrage; Super Vegeta | Big Bang/Final Flash là charge projectile P0–P1. Super Vegeta là transform P1. |
| Future Trunks | Burning Attack; Finish Buster; Buster Cannon; sword combo; Shining Sword Attack | Burning/Finish/Buster là projectile P0. Sword combo/Shining Sword cần melee frame + hitbox P0–P1. |
| Piccolo | Light Grenade; Hellzone Grenade; Special Beam Cannon; Arm Extension; Regeneration; Super Explosive Wave | Hellzone đã có backend. Light Grenade và Super Explosive Wave là area P0. Special Beam Cannon tái dùng Makankosappo với charge/beam riêng. |
| Android 17 | Android Barrier; barrier rush; barrier imprisonment; endless ki barrage | Barrier/Prison đã có nền tảng backend. Barrier rush là dash + shield P1. Endless barrage cần resource-free nhưng cooldown server P1. |
| Android 18 | Power Blitz; Infinite Energy; ki barrage; team attacks với 17 | Power Blitz/barrage P0. Infinite Energy là passive resource modifier P1. Team attack cần coordination P2. |
| Android 16 | Rocket Punch; Hell's Flash; Eye Beam; Self-Destruction | Rocket Punch là projectile vật lý P0. Hell's Flash multi-beam P1. Eye Beam P0; Self-Destruction tái dùng Tự sát. |
| Android 19 | Energy Absorption; Eye Beam | Energy Absorption đã có backend. Eye Beam dùng laser P0. |
| Dr. Gero | Energy Absorption; Eye Beam | Tái dùng Android 19; nên để boss-only nếu không muốn phá cân bằng resource. |
| Cell | Kamehameha; Solar Kamehameha; Special Beam Cannon; Kienzan; Taiyoken; Multi-Form; Android Barrier; Regeneration; Absorption; Tail Drain; Self-Destruction; Cell Junior Creation | Nhiều skill có nền tảng. Multi-Form/Cell Junior/Absorption/Tail Drain cần summon, copy hoặc drain state P1–P2. |
| Teen Gohan | Masenko; Kamehameha; Father-Son Kamehameha; SSJ2 rage burst | Masenko/Kame có sẵn. Father-Son là composite channel/team effect P1–P2. SSJ2 rage là transform + burst P1. |
| Tien | Shin Kikoho/Neo Tri-Beam | Kikoho nâng cấp với nhiều pulse, tự giới hạn HP/cooldown P1. |

**Ưu tiên:** hoàn thiện Barrier Prison và Energy Absorption trước, sau đó dùng chúng làm nền cho Android Barrier, barrier rush và Cell Absorption.

---

## 8. Majin Buu Arc

| Nhân vật | Skill trong danh sách | Hướng phát triển và khả thi |
|---|---|---|
| Goku | SSJ3; Kamehameha; Instant Transmission; Super Spirit Bomb | Kamehameha/Dịch chuyển có sẵn. SSJ3 là transform P1. Super Spirit Bomb là area charge P1. |
| Majin Vegeta | Final Impact; Final Explosion; Big Bang Attack; Final Flash | Big Bang/Final Flash mở rộng từ chưởng. Final Explosion tái dùng Tự sát nhưng damage/visual boss P1. |
| Gohan | Ultimate/Potential Unleashed; Kamehameha; Masenko | Kame/Masenko có sẵn; Potential Unleashed là buff transform P1. |
| Goten | Kamehameha; Super Saiyan | Kame có sẵn; Super Saiyan là transform P1. |
| Kid Trunks | ki blast; Super Saiyan | Ki blast P0; transform P1. |
| Goten + Trunks | Fusion Dance | Cần party interaction, kiểm tra khoảng cách và thời gian đồng bộ P2. |
| Gotenks | Super Ghost Kamikaze Attack; Galactic Donut; Die-Die Missile Barrage; Super Buu Buu Volleyball; Continuous Energy Bullet; Super Kamehameha | Super Ghost đã có backend. Galactic Donut là bind projectile P1. Die-Die/Continuous Bullet là barrage P0. Volleyball cần vùng phản hồi/đối tượng đặc biệt P2. |
| Fat Buu | Candy Beam; Healing; Regeneration; Absorption; Angry Explosion | Candy/Healing/Regeneration dùng control/buff. Absorption cần state merge P2. Angry Explosion là area P0–P1. |
| Super Buu | Human Extinction Attack; Candy Beam; Absorption; Regeneration; Angry Shout; ki barrage | Human Extinction là multi-target toàn map/zone, nên boss-only P1. Absorption/Regeneration P2. |
| Buutenks/Buuhan | Galactic Donut; Super Ghost Kamikaze Attack; Kamehameha; Special Beam Cannon; các skill của người bị hấp thụ | Cần hệ copy loadout và nhiều effect, ưu tiên boss phase P2–P3. |
| Kid Buu | Vanishing Ball; Planet Burst; Regeneration; Instant Transmission/Kai Kai bắt chước; ki barrage | Vanishing/Planet Burst là boss area P1. Regeneration và teleport P1–P2. |
| Dabura | Stone Spit; Evil Flame; sword attacks | Stone Spit tái dùng khống chế/đóng băng; Evil Flame là DoT; sword P0–P1. |
| Babidi | Manipulation Sorcery; Teleportation; Barrier; Paralysis | Sorcery/teleport/barrier/paralysis phù hợp NPC/boss utility P1. |
| Vegito | Spirit Sword; Kamehameha; Banshee Blast; barrier; Candy Vegito | Spirit Sword/Candy Vegito cần projectile và transform state P2. Kame/barrier có nền tảng. |

**Điểm đáng làm:** Galactic Donut và Galactic Buu arc là bài test tốt cho bind projectile, còn Super Ghost là bài test tốt cho summon/homing.

---

## 9. Battle of Gods

| Nhân vật | Skill trong danh sách | Hướng phát triển và khả thi |
|---|---|---|
| Beerus | Hakai; Sphere of Destruction; ki nullification; pressure-point knockout | Boss-only P2–P3. Hakai cần damage/triệt tiêu state có whitelist; Sphere là delayed area P1. |
| Goku SSG | God Kamehameha; god ki punches; energy absorption/nullification | Kamehameha biến thể P0. God ki punches là melee buff P1. Nullification mở rộng Energy Absorption nhưng cần chống vô hiệu toàn bộ skill P2. |
| Whis | Ultra Instinct-like autonomous movement; staff magic; Warp; Healing; Temporal Do-Over | Autonomous movement/warp/healing P2. Temporal Do-Over là rollback state, chỉ nên dùng event/boss scripted P3. |
| Vegeta | Rage Boost; Galick Gun; Final Flash | Galick/Final Flash P0; Rage Boost P1. |

---

## 10. Resurrection F

| Nhân vật | Skill trong danh sách | Hướng phát triển và khả thi |
|---|---|---|
| Golden Frieza | Death Beam; Emperor's Death Beam; Death Ball; Death Saucer; telekinesis; Golden transformation | Dùng lại bộ Frieza/Namek; Golden transformation là state P1–P2. |
| Goku Blue | God Kamehameha; Instant Transmission; SSB | Kame/Dịch chuyển có sẵn; SSB là transform P1. |
| Vegeta Blue | Final Flash; Galick Gun; SSB | Chưởng đã có/nên mở biến thể VFX; SSB P1. |
| Tagoma | ki blasts; high-speed combat | P0–P1, phù hợp boss thường. |
| Shisami | brute-force rush | Cận chiến P0. |

---

## 11. Universe 6 Tournament

| Nhân vật | Skill trong danh sách | Hướng phát triển và khả thi |
|---|---|---|
| Hit | Time-Skip; Vital Point Attack; Time Freeze; Tides of Time/Parallel World; Time Release | Nhóm P2. Cần server time-state, cooldown tấn công và packet reposition; không nên giả lập bằng sleep thông thường. |
| Cabba | Galick Cannon/Galick Gun-style attack; Super Saiyan | Projectile P0; transform P1. |
| Frost | Death Beam-style attack; Chaos Beam; Poison Needle | Beam P0. Poison Needle cần DoT/poison state P1. |
| Botamo | Damage Transfer/Absorption | Cần chuyển damage sang resource/target khác, P1–P2. |
| Magetta | Lava Spit; magma attacks; steam/heat body | Projectile + DoT/zone P1. Heat body là phản sát thương/kháng vật lý P1. |
| Goku | SSB Kaioken; Kamehameha | Composite transform/buff P1. |

---

## 12. Future Trunks / Goku Black Arc

| Nhân vật | Skill trong danh sách | Hướng phát triển và khả thi |
|---|---|---|
| Future Trunks | Burning Attack; Masenko; Galick Gun; Sword of Hope; Final Hope Slash; Mafuba | Burning/Masenko/Galick/Mafuba có thể tái dùng nền. Sword of Hope cần energy contribution từ đồng minh và giant sword P2. |
| Goku Black | Black Kamehameha; God Split Cut; ki blade; Sickle of Sorrow; clones/rift; Divine Lasso | Kame/ki blade P0–P1. Clones/rift/Divine Lasso cần summon, portal và bind P2. |
| Zamasu | God Split Cut; telekinesis; Kai Kai; Healing; Immortality | Healing/teleport P1; Immortality P2/P3 vì ảnh hưởng death lifecycle. |
| Fused Zamasu | Blades of Judgment; Lightning of Absolution; Wall of Light; Divine Wrath; regeneration | Projectile, lightning, shield và DoT P1. Regeneration/immortal phase P2. |
| Vegito Blue | Spirit Sword; Final Kamehameha | Composite charge beam + sword, P1–P2. |
| Goku | God Kamehameha; Instant Transmission | Tái dùng nền tảng. |
| Vegeta | Final Flash; Galick Gun | Tái dùng beam P0. |

---

## 13. Tournament of Power

| Nhân vật | Skill trong danh sách | Hướng phát triển và khả thi |
|---|---|---|
| Goku UI | Ultra Instinct; Divine Kamehameha; sliding Kamehameha; ki avatar/aura attacks | UI cần autonomous dodge/priority scheduler P2. Kame variants P0–P1. Ki avatar cần summon hitbox P2. |
| Jiren | Power Impact; Power Rush; ki glare; Kiai; energy wall; Meditation | Power Impact/Wall là area/projectile P0–P1. Kiai/ki glare là knockback/control. Meditation là boss buff hồi resource P1. |
| Toppo | Hakai; Destruction Energy; God of Destruction Mode | Boss phase P2–P3. |
| Dyspo | Super Maximum Light Speed Mode; high-speed rush | Speed state ảnh hưởng tick/movement validation P1–P2. |
| Hit | Time-Skip; Time Prison; Vital Point Attack | Dùng kiến trúc Time-Skip P2; Vital Point có thể làm critical marker P1. |
| Kefla | Gigantic Burst; Gigantic Blast; Ray Blast; ki barrage | Projectile/area P0–P1; fusion state P2. |
| Kale | Gigantic Impact; Blaster Cannon; Berserk ki barrage | Area/melee/barrage P0–P1; Berserk buff P1. |
| Caulifla | Crush Cannon; energy barrage | Projectile P0. |
| Android 17 | Android Barrier; Multiple Barrier; Barrier Prison; Grand Explode | Barrier Prison đã có. Multiple Barrier cần nhiều target/segment P1. Grand Explode là area P0–P1. |
| Android 18 | ki barrage; Infinite Energy | Tái dùng barrage/passive resource. |
| Roshi | Mafuba; Kamehameha; Thunder Shock Surprise | Mafuba/Kame có nền. Thunder Shock cần channel P1. |
| Frieza | Death Beam; Death Ball; Golden Form; Energy of Destruction manipulation | Beam/ball có thể tái dùng; Golden form P1; destruction manipulation P2. |

---

## 14. Dragon Ball Super: Broly

| Nhân vật | Skill trong danh sách | Hướng phát triển và khả thi |
|---|---|---|
| Broly | Planet Crusher; Gigantic Roar; Gigantic Breath; Gigantic Full Blast; Energy Rampage; Powered Shell; ki barrage | Boss-only P1–P2. Planet Crusher/Gigantic Roar là delayed area/beam; Powered Shell là defensive state; Energy Rampage là multi-hit rage phase. |
| Gogeta Blue | Stardust Breaker/Soul Punisher; Meteor Explosion; God Punisher; Full-Force Kamehameha | Projectile/area P1. Fusion and full-force channel P2. |
| Goku | God Bind; Kamehameha | God Bind là bind state P1; Kame có sẵn. |
| Vegeta | Galick Gun; god ki attacks | Galick có sẵn; god ki là buff damage/penetration P1. |
| Frieza | Death Beam; Death Ball; Golden Form | Tái dùng bộ Frieza. |

---

## 15. Moro / Galactic Patrol Prisoner Arc

| Nhân vật | Skill trong danh sách | Hướng phát triển và khả thi |
|---|---|---|
| Moro | Planetary Energy Absorption; Life Drain; Telekinesis; Psychic Rock Throw; Fire/Lava Manipulation; Wizard Barrier; Imprisonment Ball; Energy Shield; Illusion Clones; Mouth Beam | Đây là nhóm P2–P3. Energy/ Life Drain cần target/resource drain; Wizard Barrier/Shield dùng barrier state; Rock Throw và Mouth Beam P0–P1; Illusion Clones cần clone AI. |
| Moro 7-3 | Copy Ability; Absorption; Forced Spirit Fission; Ultra Instinct sao chép | Copy/absorption/fission/UI copy là hệ loadout động P3. Cần whitelist skill và rollback khi phase kết thúc. |
| Moro-Earth | Planet Possession; Giant Arms; Self-Destruction | Boss map phase P3; Giant Arms có thể là multi-hit zone P1 nhưng Planet Possession cần map state. |
| Vegeta | Forced Spirit Fission; Spirit Control; Instant Transmission; Final Flash | Instant/Final Flash có nền; Fission/Spirit Control cần tách buff/absorption khỏi target P2. |
| Goku | Ultra Instinct Sign; Perfected Ultra Instinct; Giant Ki Avatar; Kamehameha | UI/giant avatar P2; Kame có sẵn. |
| Merus | Ultra Instinct; angel techniques | Boss/NPC scripted P2–P3. |

---

## 16. Granolah the Survivor Arc

| Nhân vật | Skill trong danh sách | Hướng phát triển và khả thi |
|---|---|---|
| Granolah | Vital Point Shot; enhanced Cerealian eye; pinpoint ki bullets; energy rifle/bow constructs; clones; Instant Transmission; Hakai-like destruction attack | Vital Point/ki bullets là projectile có marker P1. Cerealian eye cần scan/weak-point state P2. Rifle/bow và clones cần asset/AI P1–P2. |
| Gas | Energy Weapon Creation; ki swords; spears; axes; shields; teleportation/portals; telekinesis; giant energy constructs | Weapon creation là hệ projectile/weapon runtime P2. Shield/teleport/telekinesis P1–P2. |
| Vegeta | Ultra Ego; Hakai; Sphere of Destruction; Forced Spirit Fission; Final Flash | Ultra Ego cần tăng damage theo damage nhận, P2. Hakai/fission P2–P3. Sphere/Final Flash P1. |
| Goku | UI Sign; Perfected UI; True UI; Kamehameha; Instant Transmission | UI variants P2; Kame/teleport có sẵn. |
| Black Frieza | Black Frieza transformation; high-speed physical strike | Transformation P2; strike P0–P1. |

---

## 17. Dragon Ball Super: Super Hero

| Nhân vật | Skill trong danh sách | Hướng phát triển và khả thi |
|---|---|---|
| Piccolo | Potential Unleashed; Orange Piccolo; Giant Orange Piccolo; Special Beam Cannon; Giant Form | Transform/giant state P1–P2. Special Beam Cannon tái dùng Makankosappo với VFX riêng. |
| Gohan Beast | Beast Transformation; Special Beam Cannon; Masenko; Kamehameha | Transformation P2; các chưởng còn lại có nền P0–P1. |
| Gamma 1 | Gamma Blaster; Android Barrier; melee rush | Blaster/barrier/rush P0–P1. |
| Gamma 2 | Gamma Blaster; Gamma Impact; Android Barrier; Core Breaker | Core Breaker là charge + aerial dive + area impact, P1–P2. |
| Cell Max | Mouth Energy Wave; Eye Laser; Disaster Ray; Explosive Scream; Max Barrier; Self-Destruction | Boss-only P1–P3. Laser/beam/barrier P0–P1; Disaster Ray đa hướng và Explosive Scream cần projectile fan/area lớn. |

---

## 18. Dragon Ball GT

| Nhân vật | Skill trong danh sách | Hướng phát triển và khả thi |
|---|---|---|
| Goku SSJ4 | 10x Kamehameha; Dragon Fist; Super Dragon Fist; Instant Transmission | 10x Kame là biến thể beam P0. Dragon Fist là lao xuyên/finisher P1. SSJ4 transform P1–P2. |
| Vegeta SSJ4 | Final Shine Attack; Final Flash; Galick Gun | Beam P0–P1; SSJ4 state P1. |
| Gogeta SSJ4 | Big Bang Kamehameha; Bluff Kamehameha | Composite beam và fake/decoy projectile P1–P2. |
| Baby Vegeta | Revenge Death Ball; Revenge Final Flash; Parasite Possession | Death Ball/Final Flash P1; Parasite Possession cần attach/convert target P2. |
| Super 17 | Flash Bomber; Electro Eclipse Bomb; Energy Absorption; Android Barrier | Beam/bomb/barrier; Energy Absorption đã có nền P0–P1. |
| Omega Shenron | Negative Karma Ball; Dragon Thunder; Whirlwind Spin; absorption of Dragon Balls | Boss phase P2–P3; Karma Ball/Thunder P1, Dragon Ball absorption cần event/map state. |
| Nova Shenron | flame aura; Burning Spin; fire ki | Aura/DoT/spin P0–P1. |
| Eis Shenron | ice beam; freezing attacks | Beam + freeze state P0–P1. |
| Syn Shenron | Negative Energy attacks | Boss area/projectile P1. |

---

## 19. Movie Dragon Ball Z / Dragon Ball

| Nhân vật | Skill trong danh sách | Hướng phát triển và khả thi |
|---|---|---|
| Z Broly | Eraser Cannon; Gigantic Meteor; Gigantic Slam; Omega Blaster; ki barrier | Boss projectile/area/barrier P1. |
| Cooler | Supernova; Death Beam; Death Chaser; Eye Laser | Beam/laser/area P0–P1; Supernova delayed area. |
| Metal Cooler | Instantaneous Movement; Regeneration; Energy Absorption | Teleport/regeneration/absorption P1–P2; phù hợp boss phase. |
| Janemba | Dimension Sword; portal teleport; Reality Warping; Lightning Shower Rain | Sword/lightning P1. Portal/Reality Warping P2–P3 vì ảnh hưởng vị trí và map. |
| Hirudegarn | flame breath; invisibility/intangibility; tail attacks | Breath/tail P0–P1. Invisibility/intangibility cần visibility/collision state P2. |
| Tapion | Brave Sword; ocarina sealing | Sword P0; sealing cần bind/summon state P1–P2. |
| Android 13 | S.S. Deadly Bomber; transformation | Bomber P1; transformation P1–P2. |
| Bojack | Galactic Buster; Psycho Barrier | Projectile/barrier P0–P1. |
| Turles | Kill Driver; Sudden Storm; Tree of Might Fruit | Projectile/storm P0–P1; Fruit là map/item buff P1. |
| Lord Slug | Giant Namekian; Mystic Attack; regeneration | Giant transform P2; Mystic/regeneration P1. |
| Garlic Jr. | Dead Zone; immortality; giant transformation | Dead Zone area P1; immortality/giant state P2–P3. |
| Goku | Dragon Fist | Finisher dash/projectile hybrid P1. |

---

## 20. Dragon Ball Daima

| Nhân vật | Skill/trạng thái trong danh sách | Hướng phát triển và khả thi |
|---|---|---|
| Goku Mini | Power Pole combat; Kamehameha; Super Kamehameha; SSJ/SSJ2/SSJ3; Super Saiyan 4 Daima; Super Dragon Fist | Power Pole là weapon combo P0–P1. Kame variants có nền. Các SSJ là transform chain P1–P2; SSJ4 Daima cần asset/outfit riêng. |
| Vegeta Mini | Galick Gun; Final Flash-style attacks; Super Saiyan 3 Vegeta | Galick/Final Flash P0–P1; SSJ3 transform P1–P2. |
| Glorio | lightning magic; demon magic attacks | Projectile/DoT magic P1; cần damage type và VFX mới nếu khác ki. |
| Gomah | demonic magic; energy absorption; Third Eye power | Magic/absorption P1–P2. Third Eye cần scan/vision/passive state P2. |
| Tamagami | vũ khí đặc trưng; magical ki techniques | Weapon + skill set theo boss P1–P2. |
| Majin Kuu / Majin Duu | Majin regeneration; elastic body; ki attacks | Regeneration/elastic collision state P1–P2; ki attacks P0. |

---

## 21. Các hệ thống cần xây dựng để mở rộng catalog

### 21.1. Hệ projectile dùng chung

Nên có một model server dùng cho beam, bullet, disk, sword wave và homing:

```text
Projectile {
  casterId, skillId, zoneId,
  startX, startY, targetX, targetY,
  speed, radius, lifetimeMs,
  collisionMode, maxHits, damageScale,
  effectOnHit, splashRadius
}
```

`collisionMode` có thể là `LINE`, `HOMING`, `AREA`, `RETURN`, `FAN` hoặc `ATTACH`. Khi server xử lý xong mới gửi packet effect; không để client tự quyết định hit.

### 21.2. Hệ trạng thái transform

Dùng cho Great Ape, SSJ, Golden, Blue, SSJ4, Beast, Orange, Ultra Ego và các dạng tương tự:

- lưu form hiện tại, form trước và thời gian hết hạn;
- thay đổi chỉ số theo cấu hình, không hard-code trong từng boss;
- khóa transform chồng nhau nếu không được phép;
- khôi phục outfit, hitbox, tốc độ và skill khi hết thời gian;
- ghi log khi transform bị hủy do chết, đổi map hoặc disconnect.

### 21.3. Hệ copy/absorption

Dùng cho Body Change, Cell, Buu, Moro 7-3, Energy Absorption và Parasite Possession:

- whitelist skill được copy;
- snapshot chỉ số trước khi hấp thụ;
- giới hạn thời gian/số lần;
- không copy skill quản trị, teleport map hoặc skill làm thay đổi quyền điều khiển;
- rollback đầy đủ khi phase kết thúc.

### 21.4. Hệ thời gian/không gian

Dùng cho Hit, Whis, Janemba, Goku Black và một số skill teleport:

- time token có timeout cứng;
- lưu vị trí hợp lệ trước khi skip/freeze;
- không rollback database/player inventory;
- chống dùng để thoát PK hoặc bypass map lock;
- packet phải có sequence number để client không hiển thị trạng thái cũ.

### 21.5. Hệ phối hợp nhiều nhân vật

Dùng cho Purple Comet Attack, team attacks, Father-Son Kamehameha, Fusion Dance và Sword of Hope:

- yêu cầu party/đồng minh trong cùng zone;
- kiểm tra khoảng cách và trạng thái sẵn sàng;
- một caster làm authority, các participant chỉ đóng góp resource/animation;
- hủy an toàn nếu một participant rời zone, chết hoặc đổi trạng thái.

## 22. Đề xuất backlog theo giai đoạn

### Giai đoạn A – tận dụng source hiện tại

1. Wolf Fang Fist / Neo Wolf Fang Fist.
2. Spirit Ball/Sokidan.
3. Double Tsuibikidan và Scatter Shot.
4. Galick Barrage, Death Beam và Final Flash variants.
5. Galactic Donut.
6. Android Barrier/Multiple Barrier dựa trên Barrier Prison.
7. Vital Point Shot và Poison Needle.

### Giai đoạn B – scheduler và summon

1. Die-Die Missile Barrage.
2. Chasing Bullet, Death Saucer và Energy Disk.
3. Cell Junior Creation.
4. Super Ghost nâng cấp có nhiều loại hồn ma.
5. Spirit Bomb/Super Spirit Bomb.
6. Core Breaker và các đòn dive-impact.

### Giai đoạn C – transform/resource

1. Great Ape và Giant Form.
2. Super Saiyan/Golden/Blue/SSJ4/Beast/Orange.
3. Infinite Energy, Energy Absorption mở rộng.
4. Rage Boost, Berserk và Ultra Ego.

### Giai đoạn D – boss đặc biệt

1. Time-Skip/Time Prison của Hit.
2. Body Change và hệ copy skill.
3. Hakai/Destruction Energy.
4. Planet Possession, Reality Warping, Temporal Do-Over.
5. Fusion, Sword of Hope và skill phối hợp nhiều người.

## 23. Checklist cho mỗi skill mới

- [ ] Xác định skill thuộc player, pet, boss hay NPC.
- [ ] Chọn `TYPE`, tộc, slot, `dx`, `dy`, `damage`, `max_fight`, mana và cooldown.
- [ ] Xác định skill dùng cận chiến, năng lượng, true damage, DoT hay control.
- [ ] Chọn state cần lưu trong `EffectSkill`/mob effect.
- [ ] Xác định scheduler có cần snapshot skill hay không.
- [ ] Xác định mục tiêu hợp lệ, PK, zone, map offline và giới hạn boss.
- [ ] Thêm mastery scaling và giới hạn chống spam.
- [ ] Tạo migration idempotent, bảo đảm UTF-8 và ID không trùng.
- [ ] Thêm mô tả, icon, sách học và option đọc sau khi backend ổn định.
- [ ] Tạo animation/effect x1–x4, packet client và bump version cache.
- [ ] Test caster chết, đổi map, đổi skill, mục tiêu chết và nhiều client trong cùng zone.

## 24. Kết luận

Phần lớn beam, bullet, combo, vùng nổ, buff và control trong danh sách có thể phát triển trực tiếp từ source hiện tại. Các skill nên tách thành module riêng khi có một trong các đặc điểm: thay đổi quyền điều khiển, thay đổi map, rollback thời gian, copy skill, fusion nhiều người hoặc transform làm đổi hitbox. Bốn skill đã triển khai (Hellzone Grenade, Barrier Prison, Energy Absorption, Super Ghost Kamikaze) là nền mẫu cho bốn nhóm quan trọng: delayed area, movement boundary, resource conversion và homing summon.

