# Gate 3: Controller Dispatch Model

## Compatibility boundary

`nro.models.server.Controller` remains the `IMessageHandler` installed by
`ServerManager` and retains the public methods called by `MySession`. It owns only
message cleanup, exception classification, metrics, and delegation. Numeric command
ownership lives in `nro.models.server.dispatch.ControllerCommandRegistry`.

The registry rejects duplicate command IDs at startup. Commands that require an
authenticated player are rejected before their handler runs. Unknown commands use
the legacy no-op fallback, matching the previous default switch branch.

## Frozen wire invariants

- Command IDs are signed bytes; no ID or payload order changes in this gate.
- Version-dependent widths remain in the owning handler. In particular, command
  `-100` reads consignment quantity as `int` from version 220 and as `byte` before it.
- Handlers keep existing transaction, account-protection, maintenance, player, map,
  and admin guards before gameplay mutation.
- Command `42` remains an explicit no-op and cannot fall through to Lucky Round.
- Unknown and malformed packets are contained to the dispatch call. They increment
  separate runtime metrics; exception logs are limited to five per session and
  100 globally per minute and contain no packet payload or credential.
- Image-name requests on command `66` accept only 1–64 ASCII letters, digits,
  underscore, or hyphen before filesystem lookup.

## Command ownership and payload contracts

`player` means the registry requires `session.player != null`; `session` means the
command is valid before a player is published. A delegated payload retains the
reader order implemented by the named service.

| ID | Owner | Policy | Client payload / preserved action |
|---:|---|---|---|
| -100 | economy | player | `action:byte`; consign action 0 reads `item:short, moneyType:byte, price:int, quantity:int>=v220/byte<v220`; actions 1/2/5 read `item:short`; action 3 reads `item:short, moneyType:byte, price:int`; action 4 reads two bytes. |
| -127 | economy | player | Delegated unchanged to `LuckyRound.readOpenBall`. |
| -86 | economy | player | Trade action payload delegated unchanged to `TransactionService.controller`. |
| -76 | economy | player | Optional `achievement:byte`; empty payload remains a no-op. |
| 42 | auth/asset | session | Explicit no-op registration payload; never falls through. |
| -74 | auth/asset | session | `resourceAction:byte` after asset readiness; 1 sends size, 2 sends resource data. |
| -87 | auth/asset | session | No payload; sends current data update. |
| -67 | auth/asset | session | `iconId:int`; response only after client type/zoom readiness. |
| 66 | auth/asset | session | `imageName:UTF`; allowlisted name, response only after asset readiness. |
| -66 | auth/asset | player | `effectId:short`; preserves Shenron effect-template remap. |
| -62 | auth/asset | player | `flagId:byte`; sends selected flag icon. |
| -63 | auth/asset | player | `flagId:unsigned byte`; sends flag effect icon. |
| -32 | auth/asset | session | `backgroundId:short`; response only after asset readiness. |
| -41 | auth/asset | session | `captionId:byte`. |
| 11 | auth/asset | session | `mobTemplateId:byte`. |
| -27 | auth/asset | session | No payload; session key handshake. |
| -111 | auth/asset | session | No payload; sends image-data version. |
| -28 | auth/asset | session | `subcommand:byte`; 2 create character, 6 map data, 7 skill data, 8 item data, 10 map template plus `mapId:unsigned byte`, 13 initial map completion. |
| -29 | auth/asset | session | `subcommand:byte`; 0 reads `username:UTF,password:UTF`, 2 delegates client-type payload. |
| -30 | auth/asset | session | `subcommand:byte`; 16 reads player `type:byte,point:short`; 18 reads pet `type:byte,point:short`; 64 reads `playerId:int,menuId:short`. |
| -101 | auth/asset | session | No consumed payload; sends registration/download information. |
| -38 | auth/asset | player | No payload; marks client update completion. |
| -125 | inventory/shop | player | Input payload delegated unchanged to `Input.doInput`. |
| 112 | inventory/shop | player | No payload; opens intrinsic menu. |
| -34 | inventory/shop | player | `action:byte`; 1 opens magic tree, 2 loads it. |
| -107 | inventory/shop | player | No payload; records and sends pet information. |
| -108 | inventory/shop | player | `petStatus:byte`. |
| 6 | inventory/shop | player | `buyType:byte, templateId:short`; transaction/account/maintenance guards preserved. |
| 7 | inventory/shop | player | `action:byte, bagType:byte, slot:short`; action 0 confirms, others sell. |
| -79 | inventory/shop | player | `playerId:int`; opens player menu. |
| -113 | inventory/shop | player | Up to 10 `skillId:byte`; missing bytes become `-1`. |
| -103 | inventory/shop | player | `action:byte`; action 0 opens flag UI, action 1 reads `flag:byte`. |
| -81 | inventory/shop | player | Legacy leading byte, `count:byte`, then `count` item-index bytes. |
| -40 | inventory/shop | player | Get-item payload delegated unchanged to `UseItem.getItem`. |
| -43 | inventory/shop | player | Use-item payload delegated unchanged to `UseItem.doItem`. |
| 32 | inventory/shop | player | `npcId:short, selection:byte`. |
| 33 | inventory/shop | player | `npcId:short`; opens NPC menu. |
| -105 | world/combat | player | No payload; completes the current timed map transition. |
| 29 | world/combat | player | No payload; opens zone selection. |
| 21 | world/combat | player | `zoneId:byte`. |
| -7 | world/combat | player | `movementType:byte, x:short, optional y:short`; legacy missing-coordinate behavior retained. |
| 22 | world/combat | player | Legacy byte plus `selection:byte` for magic-tree confirmation. |
| -33 | world/combat | player | No payload; waypoint transition. |
| -23 | world/combat | player | No payload; waypoint transition. |
| -45 | world/combat | player | `status:byte` followed by the existing skill payload. |
| -91 | world/combat | player | `mapSelection:byte`, interpreted by current change-map mode. |
| -39 | world/combat | player | No payload; finishes map load. |
| 34 | world/combat | player | `skillId:short`. |
| 54 | world/combat | player | `mobId:byte`; when `-1`, also `masterId:int`. |
| -60 | world/combat | player | `playerId:int`; attacks player. |
| -20 | world/combat | player | `itemMapId:short`; ignored while dead. |
| -15 | world/combat | player | No payload; returns home with legacy Ma Bư map rule. |
| -16 | world/combat | player | No payload; revives unless in tournament PK state. |
| -104 | world/combat | player | `protectionCode:int`. |
| -118 | world/combat | player | `targetId:int`; rank challenge or authorized admin boss teleport. |
| 126 | world/combat | player | `magic:byte, action:byte, castId:int, direction:unsigned byte, power:unsigned byte`. |
| -78 | world/combat | session | Consumes legacy `second:int`; no action. |
| -114 | world/combat | session | Preserved no-op. |
| 27 | world/combat | session | Preserved no-op. |
| 127 | social/clan/activity | player | `action:byte`; routes radar, clan treasury/tree/progression/shop/gift/storage, fishing book, collection, and PK history subcommands with their existing payloads. |
| -99 | social/clan/activity | player | Enemy payload delegated unchanged. |
| 18 | social/clan/activity | player | Yardrat target payload delegated unchanged. |
| -72 | social/clan/activity | player | Private-chat payload delegated unchanged. |
| -80 | social/clan/activity | player | Friend payload delegated unchanged. |
| -59 | social/clan/activity | player | Challenge payload delegated unchanged after account-protection guard. |
| -58 | social/clan/activity | player | Activity subcommand payload delegated unchanged. |
| -71 | social/clan/activity | player | `message:UTF`; global chat after transaction guard. |
| -46 | social/clan/activity | player | Get-clan payload delegated unchanged. |
| -51 | social/clan/activity | player | Clan-message payload delegated unchanged. |
| -54 | social/clan/activity | player | Clan-donate payload delegated unchanged. |
| -49 | social/clan/activity | player | Join-clan payload delegated unchanged. |
| -50 | social/clan/activity | player | `clanId:int`; requests members. |
| -56 | social/clan/activity | player | Clan-role/removal payload delegated unchanged. |
| -47 | social/clan/activity | player | `name:UTF`; searches clan. |
| -55 | social/clan/activity | player | No payload; opens leave-clan menu. |
| -57 | social/clan/activity | player | Clan-invite payload delegated unchanged. |
| 44 | social/clan/activity | player | `text:UTF`; server command/chat after transaction guard. |
| -48 | social/clan/activity | player | `hidden:byte`; only normal players mutate badge visibility. |

## Verification seam

`tools/tests/Test-Gate3RegressionSuite.ps1` full-compiles production source and
checks the frozen 78-command inventory, duplicate-owner rejection, signed command
normalization, policy rejection, legacy fallback, per-session error limiting,
malformed/unknown metrics, asset-name validation, and the small Controller facade.
Existing economy and command-level regression suites remain the behavioral gate.
