package nro.models.activity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** One deterministic-or-ranged entry in a tier reward bundle. */
public final class ActivityReward {

    public enum Kind {
        ITEM, GOLD, GEM, RUBY
    }

    private final Kind kind;
    private final int itemId;
    private final int quantityMin;
    private final int quantityMax;
    /** -1/3 is any gender; 0/1/2 follow Player.gender. */
    private final int gender;
    private final boolean bound;
    private final boolean initBaseOptions;
    private final boolean useDefaultOptions;
    private final List<ActivityRewardOption> options;
    /** Zero means permanent/no configured expiry; positive means item option 93 days. */
    private final int expiryDaysMin;
    private final int expiryDaysMax;

    public ActivityReward(Kind kind, int itemId, int quantityMin, int quantityMax, int gender,
            boolean bound, boolean initBaseOptions, boolean useDefaultOptions,
            List<ActivityRewardOption> options, int expiryDaysMin, int expiryDaysMax) {
        this.kind = kind;
        this.itemId = itemId;
        this.quantityMin = quantityMin;
        this.quantityMax = quantityMax;
        this.gender = gender;
        this.bound = bound;
        this.initBaseOptions = initBaseOptions;
        this.useDefaultOptions = useDefaultOptions;
        this.options = Collections.unmodifiableList(new ArrayList<>(options == null ? List.of() : options));
        this.expiryDaysMin = expiryDaysMin;
        this.expiryDaysMax = expiryDaysMax;
    }

    public Kind getKind() { return kind; }
    public int getItemId() { return itemId; }
    public int getQuantityMin() { return quantityMin; }
    public int getQuantityMax() { return quantityMax; }
    public int getGender() { return gender; }
    public boolean isBound() { return bound; }
    public boolean isInitBaseOptions() { return initBaseOptions; }
    public boolean isUseDefaultOptions() { return useDefaultOptions; }
    public List<ActivityRewardOption> getOptions() { return options; }
    public int getExpiryDaysMin() { return expiryDaysMin; }
    public int getExpiryDaysMax() { return expiryDaysMax; }

    public boolean requiresBagSlot() {
        return kind == Kind.ITEM;
    }
}
