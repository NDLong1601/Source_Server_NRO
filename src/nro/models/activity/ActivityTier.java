package nro.models.activity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable, published tier identity. claimBit is never derived from list position. */
public final class ActivityTier {

    private final String id;
    private final int claimBit;
    private final boolean enabled;
    private final int threshold;
    private final int minQualifiedDays;
    private final List<ActivityReward> rewards;

    public ActivityTier(String id, int claimBit, boolean enabled, int threshold,
            int minQualifiedDays, List<ActivityReward> rewards) {
        this.id = id;
        this.claimBit = claimBit;
        this.enabled = enabled;
        this.threshold = threshold;
        this.minQualifiedDays = minQualifiedDays;
        this.rewards = Collections.unmodifiableList(new ArrayList<>(rewards == null ? List.of() : rewards));
    }

    public String getId() { return id; }
    public int getClaimBit() { return claimBit; }
    public boolean isEnabled() { return enabled; }
    public int getThreshold() { return threshold; }
    public int getMinQualifiedDays() { return minQualifiedDays; }
    public List<ActivityReward> getRewards() { return rewards; }
}
