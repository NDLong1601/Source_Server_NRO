package nro.models.server;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import nro.models.utils.Logger;

public class NamedThreadFactory implements ThreadFactory {

    private final String namePrefix;
    private final boolean daemon;
    private final AtomicInteger threadIndex = new AtomicInteger(1);

    public NamedThreadFactory(String namePrefix, boolean daemon) {
        this.namePrefix = namePrefix;
        this.daemon = daemon;
    }

    @Override
    public Thread newThread(Runnable runnable) {
        Thread thread = new Thread(runnable, namePrefix + "-" + threadIndex.getAndIncrement());
        thread.setDaemon(this.daemon);
        thread.setUncaughtExceptionHandler((t, e) -> {
            Logger.error("[THREAD] event=uncaught_exception thread=" + t.getName()
                    + " errorType=" + e.getClass().getSimpleName() + "\n");
        });
        return thread;
    }
}
