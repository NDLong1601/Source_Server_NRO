package nro.models.player;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/** Serializes the existing tick body without changing its step ordering. */
public final class PlayerTickPipeline {

    private final PlayerLifecycleState lifecycle;
    private final Runnable overlapObserver;
    private final AtomicBoolean updating = new AtomicBoolean();

    public PlayerTickPipeline(PlayerLifecycleState lifecycle, Runnable overlapObserver) {
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
        this.overlapObserver = Objects.requireNonNull(overlapObserver, "overlapObserver");
    }

    public void tick(Runnable tickBody) {
        Objects.requireNonNull(tickBody, "tickBody");
        if (!lifecycle.permitsTick()) {
            return;
        }
        if (!updating.compareAndSet(false, true)) {
            overlapObserver.run();
            return;
        }
        try {
            synchronized (lifecycle.monitor()) {
                if (lifecycle.permitsTick()) {
                    tickBody.run();
                }
            }
        } finally {
            updating.set(false);
        }
    }
}
