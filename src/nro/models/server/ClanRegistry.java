package nro.models.server;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import nro.models.clan.Clan;

/** Thread-safe owner of live clans. */
public final class ClanRegistry {

    private final AtomicReference<ConcurrentHashMap<Integer, Clan>> current
            = new AtomicReference<>(new ConcurrentHashMap<>());

    public void publish(Collection<Clan> clans) {
        ConcurrentHashMap<Integer, Clan> candidate = new ConcurrentHashMap<>();
        if (clans != null) {
            for (Clan clan : clans) {
                if (clan == null) continue;
                Clan previous = candidate.putIfAbsent(clan.id, clan);
                if (previous != null) {
                    throw new IllegalArgumentException("Duplicate clan id " + clan.id);
                }
            }
        }
        current.set(candidate);
    }

    public boolean add(Clan clan) {
        if (clan == null) throw new IllegalArgumentException("clan must not be null");
        return current.get().putIfAbsent(clan.id, clan) == null;
    }

    public boolean remove(Clan clan) {
        return clan != null && current.get().remove(clan.id, clan);
    }

    public Optional<Clan> find(int id) { return Optional.ofNullable(current.get().get(id)); }
    public int size() { return current.get().size(); }

    public List<Clan> snapshot() {
        return current.get().values().stream().sorted(Comparator.comparingInt(value -> value.id)).toList();
    }
}
