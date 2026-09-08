# Gate 2: Network Backpressure and Outbound Queue Policy

## 1. Dual Bounded Outbound Queue
- **Limits**: Every session's `Sender` outbound queue is bounded by:
  1. Maximum message count: `server.sender.max_messages` (default: `1000`).
  2. Maximum serialized wire bytes: `server.sender.max_bytes` (default: `2097152` bytes / 2MB).
- **Configuration & Test Injection**: Values are loaded from `Config.properties` on startup, with programmatic accessors for test suites to inject tighter thresholds.
- **Physical Capacity**: The queue is an `ArrayBlockingQueue` created at the configured message limit; the byte and message reservations are checked and committed under the same lock as insertion.

## 2. Deterministic Fail-Closed Backpressure
- **Policy**: If an outbound packet cannot be queued without exceeding either the message count limit or the total byte ceiling, the server immediately triggers fail-closed disconnect:
  `session.close(SessionCloseCause.SLOW_CONSUMER)`
- **No Silent Packet Discard**: Packets are never silently dropped. Dropping gameplay or economy packets causes client/server state desynchronization and potential currency loss. Closing the slow or unresponsive client protects the server heap and forces clean reconciliation upon reconnect.

## 3. Byte Accounting and Wire Snapshot
- **Payload Immutability**: Outbound frames are snapshotted into `QueuedFrame` on enqueue. The exact wire size (command + length header + payload bytes) is pre-computed and added to `queuedBytes`. Subsequent caller `cleanup()` or buffer reuse cannot corrupt the payload in flight.
- **Wire Size Limits**: Commands with a two-byte length header are limited to 65,535 payload bytes. Legacy extended commands (`-32`, `-66`, `-74`, `11`, `-67`, `-87`, and `66`) use a three-byte length header and are limited to 16,777,215 payload bytes. Queued and direct sender validation plus queue wire-size accounting share the same command classification.
- **Drain and Release**: Upon session closure, all queued frames in the sender queue are cleared and total queued bytes are reset to zero exactly once.
- **Aggregate Metrics**: Queue depth and byte gauges are process-wide totals across all senders. Per-sender close/drain operations contribute deltas, preventing one session from overwriting another session's values.

## 4. Transmission Failure Handling
- **Propagating I/O Exceptions**: `MessageSendCollect.doSendMessage` allows `IOException` to propagate directly to `Sender.run()`.
- **Close Cause**: Upon encountering an unrecoverable `IOException` during frame write or flush, `Sender` closes the session with:
  `session.close(SessionCloseCause.SEND_FAILURE)`

## 5. Runtime Diagnostics
- `runtime` on the server console prints a credential-free snapshot containing active sessions/IP leases, aggregate sender queue use and high-water mark, slow-client overflows, send failures, tick duration/overruns/exceptions, rejected overlapping player updates, duplicate close attempts, and close-cause totals.
- Abnormal close events contain only session ID, cause, a redacted IP, and a cause counter. Logging is logarithmically sampled after the first ten events to prevent a disconnect storm from becoming a log-volume incident.
