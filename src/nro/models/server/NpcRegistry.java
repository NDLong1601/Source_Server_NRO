package nro.models.server;

import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import nro.models.npc.Npc;

/** Thread-safe owner of live NPC instances; duplicate template ids are allowed. */
public final class NpcRegistry {

    private final AtomicReference<List<Npc>> current = new AtomicReference<>(List.of());
    private final ThreadLocal<List<Npc>> staging = new ThreadLocal<>();

    public boolean add(Npc npc) {
        if (npc == null) throw new IllegalArgumentException("npc must not be null");
        List<Npc> candidate = staging.get();
        if (candidate != null) {
            if (candidate.contains(npc)) return false;
            candidate.add(npc);
            return true;
        }
        while (true) {
            List<Npc> snapshot = current.get();
            if (snapshot.contains(npc)) return false;
            List<Npc> updated = new ArrayList<>(snapshot);
            updated.add(npc);
            if (current.compareAndSet(snapshot, List.copyOf(updated))) return true;
        }
    }

    public boolean remove(Npc npc) {
        if (npc == null) return false;
        while (true) {
            List<Npc> snapshot = current.get();
            if (!snapshot.contains(npc)) return false;
            List<Npc> updated = new ArrayList<>(snapshot);
            updated.remove(npc);
            if (current.compareAndSet(snapshot, List.copyOf(updated))) return true;
        }
    }

    public List<Npc> snapshot() { return current.get(); }
    public List<Npc> findAll(Predicate<Npc> predicate) { return current.get().stream().filter(predicate).toList(); }

    void beginStaging() {
        if (staging.get() != null) throw new IllegalStateException("NPC staging already active");
        staging.set(new ArrayList<>());
    }

    void publishStaged() {
        List<Npc> candidate = staging.get();
        if (candidate == null) throw new IllegalStateException("NPC staging is not active");
        current.set(List.copyOf(candidate));
        staging.remove();
    }

    void abortStaging() { staging.remove(); }
}
