# Social V2 protocol contract — phase 1

Status: `LOCKED FOR PHASES 2–4`; no handler is enabled in this phase.

## Compatibility and rollout

- The existing signed top-level commands remain `-80` (social), `-72` (private-chat request), and `92` (private-chat event). No new top-level command is allowed.
- `SOCIAL_V2_CLIENT_VERSION` is **223**. A client at version 222 or below follows only the legacy payloads.
- `config/social/social_features.properties` is fail-closed: `social_v2.enabled=false` and `social_v2.migration_enabled=false` by default.
- A version-223-or-newer client sends a normal action-0 request first. It may send actions 3–12 only after it observes `CAPABILITY_SOCIAL_V2` in the action-0 response. A new client must treat a missing action-0 tail as capability `0`, so it is safe against an older server.
- Before the feature flag is enabled, action 0–2, `-72`, and `92` keep their legacy behavior exactly. New social actions must return `FEATURE_DISABLED`, never fall through to a legacy action.

## Primitive encoding

All multi-byte primitives use Java `DataInputStream`/`DataOutputStream` big-endian order:

| Token | Wire representation | Validation |
| --- | --- | --- |
| `u8` | one `writeByte` value 0–255 | reject values outside the documented range |
| `i32` | `writeInt` | IDs, cursors and request tokens must be positive where stated |
| `i64` | `writeLong` | request IDs must be positive |
| `bool` | `writeBoolean` | exactly one byte |
| `utf` | `writeUTF` | modified-UTF payload at most 65,535 bytes |

`Message.getData()` for every social payload must be at most 65,535 bytes (outer command/frame bytes are not part of this count). Inputs are trimmed before semantic validation; a search query has 2–32 Unicode code points and chat text has 1–80.

## Capability tail for action 0

The legacy action-0 response prefix is byte-for-byte unchanged:

```text
command -80
u8 action=0
u8 friendCount
repeat friendCount: i32 id, i16 head, i16 placeholder(-1), i16 body,
                    i16 leg, u8 bag, utf name, bool online, utf power
```

When `social_v2.enabled=true`, the phase-3 server appends this tail for a version-223-or-newer client after that prefix:

```text
u8 protocolVersion       # 1
i32 capabilities         # bit 0 = CAPABILITY_SOCIAL_V2
u8 friendLimit           # 100
u8 onlineFriendCount
u16 pendingRequestCount
```

When the feature is disabled, the legacy prefix is sent without this tail. No legacy client parses the tail. A client that cannot read the whole tail treats the social-v2 capability as absent.

## Actions

Action `0` — open/list friends. Legacy request and prefix above stay unchanged. The capability tail is the only gated extension.

Action `1` — legacy make-friend request: `u8 action=1, i32 targetPlayerId`. Its current menu-driven semantics and packet shape are unchanged until the v2 client path exists.

Action `2` — legacy remove friend: request `u8 action=2, i32 targetPlayerId`; legacy acknowledgement remains `u8 action=2, i32 targetPlayerId`.

Actions `3` through `12` are social-v2 only:

| Action | Direction | Request / event payload |
| ---: | --- | --- |
| `3` presence | S→C | `u8 action, i32 friendId, bool online` |
| `4` search | C→S | `u8 action, i32 requestToken, i32 cursor, utf query` |
| `4` search page | S→C | `u8 action=4, u8 result=0, i32 requestToken, i32 nextCursor, bool hasMore, u8 count`, followed by at most 20 entries: `i32 playerId, i16 head, utf name, u8 relationship` (`0=FRIEND`, `1=PENDING`, `2=CAN_ADD`) |
| `5` send request | C→S | `u8 action, i32 targetPlayerId` |
| `6` inbox | C→S | `u8 action, i32 requestToken, i32 cursor` |
| `6` inbox page | S→C | `u8 action=6, u8 result=0, i32 requestToken, i32 nextCursor, bool hasMore, u8 count`, followed by at most 20 entries: `i64 requestId, i32 senderId, i16 head, utf senderName, i64 expiresAtEpochMillis` |
| `7` accept | C→S | `u8 action, i64 requestId` |
| `8` reject/delete | C→S | `u8 action, i64 requestId` |
| `9` profile | C→S | `u8 action, i32 friendId` |
| `10` location | C→S | `u8 action, i32 friendId`; coordinates never come from the client |
| `11` location event | S→C | `u8 action, i32 senderId, i16 mapId, i16 zoneId, i16 x, i16 y` |
| `12` pending count | S→C | `u8 action, u16 pendingRequestCount` |

Every page response echoes the request token, emits an opaque next cursor, and has `count <= 20`. Search results are ordered exact numeric ID, exact name, name prefix, then name contains; server-side SQL must escape `%` and `_`.

## Phase-4 profile, presence, chat, and location behavior

Action `9` returns the normal error envelope on rejection. A successful profile response is:

```text
u8 action=9, u8 result=0, i32 friendId, i16 head, utf name, utf clanName,
utf activityLabel, i64 rawPower, utf formattedPower, bool online
```

The server checks the normalized friendship before looking up a profile. An online target is projected from its published `Player`; an offline target is read with a narrow `player`/`clan`/logout-time projection. Account credentials, account identifiers, email, IP, inventory, and chat history are never selected or serialized. Activity labels are server policy: online = `Đang online`; last activity at most 7 days = `Thường xuyên`; 8–30 days = `Gần đây`; otherwise = `Ít hoạt động`.

Presence action `3` is emitted only to online, current friends that support Social V2. Publication happens after the session/player login lifecycle is complete. Session teardown removes the player from the social online index before emitting `online=false`; chat and location take the same interaction lock, so a delivery beginning after unpublish cannot reach the closed player.

For a Social-V2 sender, `-72` is accepted only for a current mutual friendship whose target remains in the online index. Text is trimmed, must contain 1–80 Unicode code points, and rejects ISO control characters. A bounded token bucket permits three messages per second per sender. On success the legacy packet `92` is still emitted to sender and recipient in its existing order; no server log or persistence contains message text. An offline race produces a best-effort action-3 `online=false` correction and no sender echo.

Action `10` accepts only `friendId`; map, zone, x, and y never arrive from the client. The server rechecks friendship and online state, applies a two-minute sender cooldown after a successful delivery, snapshots the current server-side location, then emits action `11` to both sender and recipient:

```text
u8 action=11, i32 senderId, i16 mapId, i16 zoneId, i16 x, i16 y
```

This lets the sender render the same server-authoritative location event as the recipient. A malformed or unavailable server location fails without emitting coordinates.

## Phase-3 anti-abuse policy

- Search is limited per authenticated player ID to **30 accepted actions in a rolling 60-second window**.
- Send-request is limited per authenticated player ID to **one action every 5 seconds** and **10 accepted actions in a rolling 10-minute window**. The counter applies even when the relationship outcome is duplicate, full, expired, or not-found, so repeated invalid actions cannot bypass it.
- A limited action returns the normal v2 error envelope with `RATE_LIMITED` and a generic user notification. The action's caller identity always comes from the session; no client-supplied ID participates in the rate-limit key.
- The local-mode server keeps only bounded process-local state: at most 10,000 recently active player IDs, with idle entries evicted after 15 minutes. A future multi-process deployment must replace this with a shared limiter before enabling Social V2.

## Error envelope

For v2 request/response actions that do not have a page/event-specific payload, the response starts:

```text
u8 action
u8 result                 # 0=OK, 1=ERROR
if result == 1: u8 errorCode
```

| Error code | Name |
| ---: | --- |
| `1` | `FEATURE_DISABLED` |
| `2` | `CLIENT_TOO_OLD` |
| `3` | `MALFORMED` |
| `4` | `UNSUPPORTED_ACTION` |
| `5` | `NOT_FRIENDS` |
| `6` | `NOT_FOUND` |
| `7` | `SELF_TARGET` |
| `8` | `FRIEND_LIMIT` |
| `9` | `DUPLICATE` |
| `10` | `EXPIRED` |
| `11` | `OFFLINE` |
| `12` | `RATE_LIMITED` |
| `13` | `INVALID_TEXT` |
| `14` | `LOCATION_COOLDOWN` |
| `15` | `PACKET_TOO_LARGE` |

`-72` and packet `92` retain their legacy payload order for old clients: request `i32 targetPlayerId, utf text`; event `utf senderName, utf decoratedText, i32 senderId, i16 head, [i16 placeholder for version >214], i16 body, i16 bag, i16 leg, u8 chatType`. V2 chat enforcement changes only server authorization in phase 4 and must not reorder this payload.
