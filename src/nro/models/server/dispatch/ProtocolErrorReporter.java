package nro.models.server.dispatch;

import nro.models.server.ServerRuntimeMetrics;
import nro.models.utils.Logger;

public final class ProtocolErrorReporter {

    private static final int MAX_LOGGED_ERRORS_PER_WINDOW = 5;
    private static final int MAX_GLOBAL_LOGGED_ERRORS_PER_WINDOW = 100;
    private static final long ERROR_WINDOW_MILLIS = 60_000L;
    private static final Object GLOBAL_LOG_IDENTITY = new Object();
    private final ProtocolErrorRateLimiter limiter = new ProtocolErrorRateLimiter(
            MAX_LOGGED_ERRORS_PER_WINDOW, ERROR_WINDOW_MILLIS);
    private final ProtocolErrorRateLimiter globalLimiter = new ProtocolErrorRateLimiter(
            MAX_GLOBAL_LOGGED_ERRORS_PER_WINDOW, ERROR_WINDOW_MILLIS);

    public void recordUnknown(CommandContext context) {
        ServerRuntimeMetrics.gI().recordProtocolError(ProtocolErrorType.UNKNOWN_COMMAND);
    }

    public void report(ProtocolErrorType type, CommandContext context, Exception error) {
        ServerRuntimeMetrics.gI().recordProtocolError(type);
        long now = System.currentTimeMillis();
        if (!limiter.tryAcquire(context.session(), now)
                || !globalLimiter.tryAcquire(GLOBAL_LOG_IDENTITY, now)) {
            return;
        }
        Logger.errorln("[PROTOCOL] event=command_error sessionId=" + context.session().getID()
                + " command=" + context.command()
                + " category=" + type
                + " errorType=" + error.getClass().getSimpleName());
        Logger.logException(ProtocolErrorReporter.class, error);
    }
}
