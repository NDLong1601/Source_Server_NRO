# Technology stack

- **Language/runtime:** Java 17 (`nbproject/project.properties:67-68`).
- **Build system:** Apache Ant/NetBeans (`build.xml`, `nbproject/build-impl.xml`).
- **Application entry point:** `nro.models.server.ServerManager` (`nbproject/project.properties:94`).
- **Transport:** non-blocking NIO `ServerSocketChannel`/`Selector` in `network/Network.java`.
- **Persistence:** JDBC DAOs using `LocalManager.getConnection()`; MySQL connector is declared in NetBeans classpath.
- **Libraries:** HikariCP, Gson, json-simple, Apache Commons Lang, Lombok, SLF4J, MySQL connector under `lib/`.
- **Configuration:** root `.properties` files, `Config.properties`, `activity.properties`, and runtime data files.
- **Operations:** Windows `.bat`, PowerShell, HTA admin/dashboard tools.
- **Tests/tools:** Java tests under `tools/tests`, plus a .NET ghost-client test tool.

## Compatibility and architecture blockers

- NetBeans/Ant metadata is the source of truth; there is no Maven/Gradle manifest.
- `Manager` and `Controller` are high fan-in/fan-out classes and should be treated as shared modules.
- `ServerManager` starts many independent threads directly, so lifecycle and shutdown behavior is distributed.
- Package names include mixed casing (`Bot`, `daily_Giftcode`) and mixed naming conventions; normalize only with a coordinated package/import/reflection change.
- `activity.properties` is intentionally fail-closed and shadow-only; it is configuration, not dead code.
