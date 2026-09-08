package nro.models.server;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/** Atomically published owner of Map/Zone runtime state. */
public final class WorldRegistry {

    private record State(List<nro.models.map.Map> worlds, Map<Integer, nro.models.map.Map> byId) {
    }

    private final AtomicReference<State> state = new AtomicReference<>(new State(List.of(), Map.of()));

    public void publish(List<nro.models.map.Map> candidate) {
        if (candidate == null) throw new IllegalArgumentException("worlds must not be null");
        List<nro.models.map.Map> snapshot = List.copyOf(candidate);
        Map<Integer, nro.models.map.Map> byId = new HashMap<>();
        for (nro.models.map.Map map : snapshot) {
            if (map.mapId < 0 || map.mapId > 254) {
                throw new IllegalArgumentException("map runtime ID is outside packet range: " + map.mapId);
            }
            if (byId.put(map.mapId, map) != null) {
                throw new IllegalArgumentException("duplicate map runtime ID: " + map.mapId);
            }
        }
        state.set(new State(snapshot, Map.copyOf(byId)));
    }

    public List<nro.models.map.Map> snapshot() { return state.get().worlds(); }

    public nro.models.map.Map get(int mapId) {
        return state.get().byId().get(mapId);
    }
}
