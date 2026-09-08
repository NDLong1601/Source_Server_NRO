package nro.models.player;

import java.util.Collections;
import java.util.List;

/**
 * Immutable projection of a prepared wallet mutation plan.
 * Used for workflows where the database is authoritative before RAM is updated.
 */
public final class PreparedWalletMutation {

    private final PlayerWallet owner;
    private final WalletSnapshot beforeSnapshot;
    private final WalletSnapshot afterSnapshot;
    private final List<WalletLeg> legs;
    private final WalletMutationContext context;

    PreparedWalletMutation(PlayerWallet owner, WalletSnapshot beforeSnapshot,
                           WalletSnapshot afterSnapshot, List<WalletLeg> legs,
                           WalletMutationContext context) {
        this.owner = owner;
        this.beforeSnapshot = beforeSnapshot;
        this.afterSnapshot = afterSnapshot;
        this.legs = legs != null ? List.copyOf(legs) : Collections.emptyList();
        this.context = context;
    }

    public WalletSnapshot getBeforeSnapshot() {
        return beforeSnapshot;
    }

    public WalletSnapshot getAfterSnapshot() {
        return afterSnapshot;
    }

    public List<WalletLeg> getLegs() {
        return legs;
    }

    public WalletMutationContext getContext() {
        return context;
    }

    boolean belongsTo(PlayerWallet wallet) {
        return owner == wallet;
    }

    @Override
    public String toString() {
        return "PreparedWalletMutation{"
                + "before=" + beforeSnapshot
                + ", after=" + afterSnapshot
                + ", legs=" + legs
                + ", context=" + context
                + '}';
    }
}
