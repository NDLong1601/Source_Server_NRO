package nro.models.player;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Single owner for the transient lifecycle of a player aggregate. State changes
 * are monotonic so disconnect, tick, mailbox, and dispose cannot reopen a player.
 */
public final class PlayerLifecycleState {

    public enum Phase {
        ACTIVE,
        REMOVING,
        DISPOSING,
        DISPOSED
    }

    private final AtomicReference<Phase> phase = new AtomicReference<>(Phase.ACTIVE);
    private final Object monitor = new Object();

    public Phase phase() {
        return phase.get();
    }

    public boolean permitsTick() {
        return phase.get() == Phase.ACTIVE;
    }

    public boolean acceptsRuntimeWork() {
        return phase.get() == Phase.ACTIVE;
    }

    public boolean isDisposed() {
        return phase.get() == Phase.DISPOSED;
    }

    public boolean isRemovingOrDisposed() {
        return phase.get() != Phase.ACTIVE;
    }

    public boolean beginRemoval() {
        return phase.compareAndSet(Phase.ACTIVE, Phase.REMOVING);
    }

    public boolean beginDisposal() {
        while (true) {
            Phase current = phase.get();
            if (current == Phase.DISPOSING || current == Phase.DISPOSED) {
                return false;
            }
            if (phase.compareAndSet(current, Phase.DISPOSING)) {
                return true;
            }
        }
    }

    public void markDisposed() {
        if (!phase.compareAndSet(Phase.DISPOSING, Phase.DISPOSED)
                && phase.get() != Phase.DISPOSED) {
            throw new IllegalStateException("Cannot finish player lifecycle from " + phase.get());
        }
    }

    public Object monitor() {
        return monitor;
    }

}
