package nro.models.player;

import java.util.Map;

public record PlayerPersistenceToken(long expectedSaveVersion,
        Map<PlayerPersistenceComponent, Long> componentVersions) {

    public PlayerPersistenceToken {
        componentVersions = componentVersions == null ? Map.of() : Map.copyOf(componentVersions);
    }
}
