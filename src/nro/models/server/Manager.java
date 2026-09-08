package nro.models.server;

/**
 * Gate 4 compatibility shell. Production code must depend on the typed owners in
 * {@link GameRuntime}; the dependency gate prevents new direct callers.
 */
@Deprecated
public final class Manager {

    private static final Manager INSTANCE = new Manager();

    private Manager() {
    }

    public static Manager gI() {
        GameRuntime.gI();
        return INSTANCE;
    }

    public void shutdownRuntimeExecutors() {
        GameRuntime.gI().shutdown();
    }
}
