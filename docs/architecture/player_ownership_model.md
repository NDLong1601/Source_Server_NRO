# Gate 2: Player Ownership Model

## 1. Single Tick Ownership
- **Tick Owner**: `Zone.update()` is the sole owner responsible for invoking `Player.update()`.
- **Elimination of Per-Player Threads**: The legacy pattern in `Player.start()` and `Player.run()` that spawned an unowned `Executors.newSingleThreadExecutor()` per login is completely removed. Neither `Player.start()` nor `Controller.sendInfo()` creates a worker thread.
- **World Tick Scheduling**: World tick scheduling in `Manager` utilizes `scheduleWithFixedDelay` with non-overlapping execution. A slow zone update delays the subsequent tick cycle rather than overlapping it.

## 2. Concurrency and Mutual Exclusion Guarantees
- **Non-Overlapping Update Guard**: `Player.update()` is guarded by an atomic CAS transition (`AtomicBoolean isUpdating.compareAndSet(false, true)`). If a tick cycle or overlapping thread attempts to invoke `update()` while another is already executing, the duplicate invocation is immediately rejected and recorded in `ServerRuntimeMetrics.rejectedOverlapCount`.
- **Lifecycle & Persistence Serialization**: `Player.update()`, player logout, save (`PlayerDAO.updatePlayer`), and `Player.dispose()` are serialized under an internal monitor (`lifecycleLock`). While `update()` runs, disposal cannot null fields. While disposal or save runs, `update()` cannot execute.

## 3. Cross-Thread Mailbox Boundary
- **PlayerMailbox**: Each connected `Player` instance owns a bounded serial FIFO mailbox (`PlayerMailbox`) with a capacity of 128 tasks.
- **Owner-Thread Execution**: `submit()` only appends work. It never executes work on the caller. The owning `Zone` drains the mailbox immediately before that player's update, so accepted tasks run serially in FIFO order on a zone worker. The drain holds the same lifecycle monitor used by update, save, and dispose.
- **Scope**: The mailbox is the boundary for newly migrated cross-thread player mutations. Existing transition paths not yet migrated remain protected by the lifecycle lock and the update CAS guard; this document does not imply that every map or gameplay handler already uses the mailbox.
- **Fail-Closed on Teardown**: When a player is disconnected or disposed, the mailbox is closed. Submit, poll, and close are linearized on the queue lock, so a submit racing teardown cannot leave retained work; subsequently submitted tasks are rejected (`submit()` returns `false`).

## 4. Registry and Teardown Identity
- **Atomic Registry View**: Player ID, account ID, name, and ordered player-list indexes are changed under one registry lock and exposed as snapshots.
- **Stale-Removal Safety**: Registry removal is conditional on object identity. Teardown of an old login cannot remove a replacement login that reuses the same ID, account, or name.
- **Single-Winner Removal**: `Player.beginRemoval()` admits only one cleanup/save/dispose lifecycle. Network close races, duplicate-login kicks, and administrative kicks therefore cannot persist or dispose the same instance more than once.
