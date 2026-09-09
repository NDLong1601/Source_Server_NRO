package nro.models.player;

/** Coarse dirty partitions used to coalesce autosaves without losing mutations. */
public enum PlayerPersistenceComponent {
    CORE,
    POSITION,
    COMBAT,
    INVENTORY,
    EVENT,
    SOCIAL,
    ACTIVITY
}
