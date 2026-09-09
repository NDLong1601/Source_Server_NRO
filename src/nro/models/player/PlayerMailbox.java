package nro.models.player;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import nro.models.utils.Logger;

public final class PlayerMailbox {

    private final Player player;
    private final BlockingQueue<Runnable> queue;
    private final Object queueLock = new Object();
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicBoolean draining = new AtomicBoolean(false);

    public PlayerMailbox(Player player, int capacity) {
        this.player = player;
        this.queue = new ArrayBlockingQueue<>(capacity > 0 ? capacity : 128);
    }

    public boolean submit(Runnable task) {
        if (task == null) {
            return false;
        }
        synchronized (queueLock) {
            if (closed.get() || (player != null && !player.acceptsRuntimeWork())) {
                return false;
            }
            if (!queue.offer(task)) {
                Logger.warning("[MAILBOX] Player mailbox queue full for player id="
                        + (player != null ? player.id : -1) + "; task rejected\n");
                return false;
            }
            // Player.dispose() publishes disposed before it waits for the
            // lifecycle lock. Do not report acceptance if that transition won.
            if (player != null && !player.acceptsRuntimeWork()) {
                queue.clear();
                return false;
            }
            return true;
        }
    }

    public void drain() {
        if (!draining.compareAndSet(false, true)) {
            return;
        }
        try {
            Object lifecycleLock = player != null ? player.getLifecycleLock() : this;
            synchronized (lifecycleLock) {
                if (closed.get() || (player != null && !player.acceptsRuntimeWork())) {
                    synchronized (queueLock) {
                        queue.clear();
                    }
                    return;
                }
                Runnable task;
                while ((task = pollNext()) != null) {
                    try {
                        task.run();
                    } catch (Throwable t) {
                        Logger.error("[MAILBOX] Exception executing task for player "
                                + (player != null ? player.id : -1) + ": " + t.getMessage() + "\n");
                    }
                }
            }
        } finally {
            draining.set(false);
        }
    }

    public void close() {
        synchronized (queueLock) {
            closed.set(true);
            queue.clear();
        }
    }

    public boolean isClosed() {
        return closed.get();
    }

    public int getPendingCount() {
        return queue.size();
    }

    private Runnable pollNext() {
        synchronized (queueLock) {
            if (closed.get() || (player != null && !player.acceptsRuntimeWork())) {
                queue.clear();
                return null;
            }
            return queue.poll();
        }
    }
}
