package nro.models.server.dispatch;

import java.util.Map;
import java.util.WeakHashMap;

/** Per-session log limiter; weak keys avoid extending a closed session's lifetime. */
public final class ProtocolErrorRateLimiter {

    private final int allowance;
    private final long windowMillis;
    private final Map<Object, Window> windows = new WeakHashMap<>();

    public ProtocolErrorRateLimiter(int allowance, long windowMillis) {
        if (allowance < 1 || windowMillis < 1) {
            throw new IllegalArgumentException("allowance and windowMillis must be positive");
        }
        this.allowance = allowance;
        this.windowMillis = windowMillis;
    }

    public synchronized boolean tryAcquire(Object sessionIdentity, long nowMillis) {
        if (sessionIdentity == null) {
            return false;
        }
        Window window = windows.get(sessionIdentity);
        if (window == null || nowMillis - window.startedAt >= windowMillis || nowMillis < window.startedAt) {
            windows.put(sessionIdentity, new Window(nowMillis, 1));
            return true;
        }
        if (window.count >= allowance) {
            return false;
        }
        window.count++;
        return true;
    }

    private static final class Window {

        private final long startedAt;
        private int count;

        private Window(long startedAt, int count) {
            this.startedAt = startedAt;
            this.count = count;
        }
    }
}
