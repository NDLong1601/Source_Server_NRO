package nro.models.player;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/** Tracks optimistic DB revision and per-component dirty generations. */
public final class PlayerPersistenceState {

    private final EnumMap<PlayerPersistenceComponent, Long> versions =
            new EnumMap<>(PlayerPersistenceComponent.class);
    private final EnumMap<PlayerPersistenceComponent, Long> acknowledged =
            new EnumMap<>(PlayerPersistenceComponent.class);
    private final AtomicBoolean saveInFlight = new AtomicBoolean();
    private long saveVersion;

    public PlayerPersistenceState() {
        for (PlayerPersistenceComponent component : PlayerPersistenceComponent.values()) {
            versions.put(component, 1L);
            acknowledged.put(component, 0L);
        }
    }

    public synchronized void initializeLoaded(long loadedSaveVersion) {
        if (loadedSaveVersion < 0L) {
            throw new IllegalArgumentException("saveVersion must not be negative");
        }
        saveVersion = loadedSaveVersion;
        for (PlayerPersistenceComponent component : PlayerPersistenceComponent.values()) {
            acknowledged.put(component, versions.get(component));
        }
    }

    public synchronized void markDirty(PlayerPersistenceComponent component) {
        versions.compute(component, (ignored, value) -> value == null ? 1L : value + 1L);
    }

    public synchronized void markDirty(PlayerPersistenceComponent first,
            PlayerPersistenceComponent... remaining) {
        markDirty(first);
        if (remaining != null) {
            for (PlayerPersistenceComponent component : remaining) {
                if (component != null) {
                    markDirty(component);
                }
            }
        }
    }

    public synchronized boolean isDirty() {
        for (PlayerPersistenceComponent component : PlayerPersistenceComponent.values()) {
            if (!versions.get(component).equals(acknowledged.get(component))) {
                return true;
            }
        }
        return false;
    }

    public synchronized Set<PlayerPersistenceComponent> dirtyComponents() {
        EnumSet<PlayerPersistenceComponent> dirty = EnumSet.noneOf(PlayerPersistenceComponent.class);
        for (PlayerPersistenceComponent component : PlayerPersistenceComponent.values()) {
            if (!versions.get(component).equals(acknowledged.get(component))) {
                dirty.add(component);
            }
        }
        return dirty;
    }

    public synchronized PlayerPersistenceToken captureToken() {
        EnumMap<PlayerPersistenceComponent, Long> dirty =
                new EnumMap<>(PlayerPersistenceComponent.class);
        for (PlayerPersistenceComponent component : PlayerPersistenceComponent.values()) {
            if (!versions.get(component).equals(acknowledged.get(component))) {
                dirty.put(component, versions.get(component));
            }
        }
        return new PlayerPersistenceToken(saveVersion, dirty);
    }

    public synchronized void acknowledge(PlayerPersistenceToken token, long committedSaveVersion) {
        if (token == null || committedSaveVersion != token.expectedSaveVersion() + 1L) {
            throw new IllegalArgumentException("Invalid persistence acknowledgement");
        }
        if (saveVersion != token.expectedSaveVersion()) {
            throw new IllegalStateException("Persistence acknowledgement is stale");
        }
        for (Map.Entry<PlayerPersistenceComponent, Long> entry : token.componentVersions().entrySet()) {
            if (entry.getValue().equals(versions.get(entry.getKey()))) {
                acknowledged.put(entry.getKey(), entry.getValue());
            }
        }
        saveVersion = committedSaveVersion;
    }

    public synchronized long saveVersion() {
        return saveVersion;
    }

    public boolean tryBeginSave() {
        return saveInFlight.compareAndSet(false, true);
    }

    public void finishSave() {
        saveInFlight.set(false);
        synchronized (saveInFlight) {
            saveInFlight.notifyAll();
        }
    }

    public boolean awaitSaveCompletion(long timeoutMillis) {
        long deadline = System.currentTimeMillis() + Math.max(0L, timeoutMillis);
        synchronized (saveInFlight) {
            while (saveInFlight.get()) {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0L) {
                    return false;
                }
                try {
                    saveInFlight.wait(remaining);
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
            return true;
        }
    }
}
