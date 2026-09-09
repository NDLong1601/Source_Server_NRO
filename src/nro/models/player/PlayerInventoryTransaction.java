package nro.models.player;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Aggregate transaction boundary for operations that change wallet and bag
 * together. Runtime exceptions restore the pre-operation wallet and bag state.
 */
public final class PlayerInventoryTransaction {

    private PlayerInventoryTransaction() {
    }

    public static <T> T execute(Player player, Supplier<T> operation) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(operation, "operation");
        if (player.inventory == null || player.isRemovingOrDisposed()
                || player.isPersistenceQuarantined()) {
            throw new IllegalStateException("Player inventory is not available for mutation");
        }
        synchronized (player.inventory) {
            InventoryPersistenceSnapshot before = InventoryPersistenceSnapshot.capture(player);
            try {
                T result = operation.get();
                player.getPersistenceState().markDirty(PlayerPersistenceComponent.INVENTORY);
                return result;
            } catch (RuntimeException | Error failure) {
                before.applyTo(player);
                throw failure;
            }
        }
    }
}
