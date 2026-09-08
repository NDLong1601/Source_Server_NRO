# Gate 2: Session Lifecycle Model

## State Machine

Sessions move monotonically from `NEW` to `ACTIVE`, then through `CLOSING` to `CLOSED`. `close(cause)` uses a retrying atomic transition, so activation racing with close cannot cause a terminal request to be lost. The first close cause wins; later close attempts only increment the duplicate-close metric.

## Resource Ownership

The session owns its IP lease, sender, collector, worker threads, socket, registry membership, and subclass teardown hook. Close executes each cleanup step independently so one failing hook cannot skip lease release, socket closure, or queue cleanup. Network resources and the IP lease are released before player persistence begins.

An IP lease assigned after close has already started is released immediately. Sender startup and collector startup are independently idempotent, preventing handshake retries from calling `Thread.start()` twice.

Socket shutdown verification can wait for both owned workers through `Session.awaitTermination`; it never starts or closes a session by itself.

## Accept and Shutdown Races

`SessionManager.putSession` rechecks terminal state after publishing a session. This prevents an accept thread from inserting a session after its close path already removed it. During shutdown, registration is disabled before the registry snapshot is closed; any in-flight accepted session is closed with the shutdown cause instead of being admitted.

`ServerManager.close()` stops new accepts, closes every registered session (including unauthenticated sessions without a `Player`), completes the player fallback sweep, persists clan/consignment shared state only after clients are quiescent, and then shuts down the owned runtime executors. Each shutdown phase is failure-isolated so a broken phase cannot skip later cleanup.

## Credentials and Logging

Raw credentials are never included in lifecycle logs and are cleared from `MySession` after the close hook. IP addresses are redacted in close diagnostics. Login timestamp/IP persistence uses a parameterized database update.

Authentication throttling and reconnect client profiles use bounded IP-scoped caches. Both caches cap retained addresses at 10,000; anti-login entries expire after 10 minutes of inactivity and client profiles after 30 minutes.
