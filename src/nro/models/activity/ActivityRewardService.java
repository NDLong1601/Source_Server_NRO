package nro.models.activity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import nro.models.database.PlayerDAO;
import nro.models.item.Item;
import nro.models.player.Player;
import nro.models.player.PlayerConfig;
import nro.models.services.InventoryService;
import nro.models.services.ItemService;
import nro.models.services.PlayerService;
import nro.models.services.RewardService;
import nro.models.services.Service;

/** Tier eligibility and all-or-nothing reward delivery for Activity Points. */
public final class ActivityRewardService {

    private static final ActivityRewardService INSTANCE = new ActivityRewardService();

    private ActivityRewardService() {
    }

    public static ActivityRewardService gI() {
        return INSTANCE;
    }

    public List<TierStatus> getTierStatuses(Player player, ActivityPeriodType period) {
        if (player == null || period == null) {
            return List.of();
        }
        ActivityService.gI().ensureCurrentPeriod(player, false, true);
        ActivityConfig config = ActivityConfigService.gI().getCurrent();
        ActivityState state = player.activityState;
        if (state == null) {
            return List.of();
        }
        synchronized (state) {
            List<TierStatus> result = new ArrayList<>();
            for (ActivityTier tier : tiers(config, period)) {
                result.add(evaluate(state, period, tier));
            }
            return Collections.unmodifiableList(result);
        }
    }

    public ActivityClaimResult claimDaily(Player player, String tierId) {
        return claim(player, ActivityPeriodType.DAILY, tierId);
    }

    public ActivityClaimResult claimWeekly(Player player, String tierId) {
        return claim(player, ActivityPeriodType.WEEKLY, tierId);
    }

    public ActivityClaimResult claim(Player player, ActivityPeriodType period, String tierId) {
        if (player == null || period == null || tierId == null || tierId.isBlank()) {
            return ActivityClaimResult.of(ActivityClaimResult.Reason.TIER_NOT_FOUND, "Mốc phần thưởng không hợp lệ.");
        }
        ActivityService.gI().ensureCurrentPeriod(player, false, true);
        ActivityConfig config = ActivityConfigService.gI().getCurrent();
        ActivityState state = player.activityState;
        if (state == null) {
            return ActivityClaimResult.of(ActivityClaimResult.Reason.NOT_ELIGIBLE, "Dữ liệu Năng động chưa sẵn sàng.");
        }
        synchronized (state) {
            ActivityTier tier = findTier(config, period, tierId);
            if (tier == null) {
                return ActivityClaimResult.of(ActivityClaimResult.Reason.TIER_NOT_FOUND, "Mốc phần thưởng không tồn tại.");
            }
            if (!config.isEnabled()) {
                return ActivityClaimResult.of(ActivityClaimResult.Reason.FEATURE_DISABLED, "Năng động hiện đang tạm khóa.");
            }
            // Shadow mode may never distribute production rewards, even if an
            // operator accidentally turns on rewardEnabled in the same revision.
            if (!config.isRewardsEnabled() || config.isShadowMode()) {
                return ActivityClaimResult.of(ActivityClaimResult.Reason.REWARDS_DISABLED,
                        "Phần thưởng Năng động chưa mở; điểm của bạn vẫn được giữ lại.");
            }
            if (!tier.isEnabled()) {
                return ActivityClaimResult.of(ActivityClaimResult.Reason.TIER_DISABLED, "Mốc phần thưởng này đang tạm đóng.");
            }
            TierStatus status = evaluate(state, period, tier);
            if (status.isClaimed()) {
                return ActivityClaimResult.of(ActivityClaimResult.Reason.ALREADY_CLAIMED, "Bạn đã nhận mốc này rồi.");
            }
            if (!status.isEligible()) {
                return ActivityClaimResult.of(ActivityClaimResult.Reason.NOT_ELIGIBLE, "Bạn chưa đủ điều kiện nhận mốc này.");
            }

            RewardBundle bundle = buildBundle(player, tier);
            if (bundle.failure != null) {
                return bundle.failure;
            }
            if (InventoryService.gI().getCountEmptyBag(player) < bundle.items.size()) {
                return ActivityClaimResult.of(ActivityClaimResult.Reason.INVENTORY_FULL,
                        "Hành trang cần ít nhất " + bundle.items.size() + " ô trống để nhận quà.");
            }
            if (wouldOverflowCurrency(player, bundle)) {
                return ActivityClaimResult.of(ActivityClaimResult.Reason.INVALID_REWARD,
                        "Tiền tệ hiện tại đã chạm giới hạn, chưa thể nhận trọn gói quà.");
            }
            if (!ActivityClaimAuditService.gI().reserve(player, period, tier, config, state, bundle.delivered)) {
                return ActivityClaimResult.of(ActivityClaimResult.Reason.ALREADY_CLAIMED,
                        "Mốc này đã được nhận hoặc đang được hệ thống xử lý.");
            }

            List<Item> bagBefore = copyBag(player);
            try {
                for (Item item : bundle.items) {
                    if (!InventoryService.gI().addItemList(player.inventory.itemsBag, item)) {
                        restoreBag(player, bagBefore);
                        ActivityClaimAuditService.gI().release(player, period, tier, state);
                        return ActivityClaimResult.of(ActivityClaimResult.Reason.DELIVERY_FAILED,
                                "Không thể thêm trọn gói quà vào hành trang. Vui lòng thử lại.");
                    }
                }
            } catch (Exception e) {
                restoreBag(player, bagBefore);
                ActivityClaimAuditService.gI().release(player, period, tier, state);
                return ActivityClaimResult.of(ActivityClaimResult.Reason.DELIVERY_FAILED,
                        "Không thể thêm trọn gói quà vào hành trang. Vui lòng thử lại.");
            }
            player.inventory.gold += bundle.gold;
            player.inventory.gem += (int) bundle.gem;
            player.inventory.ruby += (int) bundle.ruby;
            setClaimed(state, period, tier.getClaimBit());

            // Persist while holding the same activity-state lock used for the
            // eligibility check. A double-click cannot pass the mask check.
            PlayerDAO.updatePlayer(player);
            ActivityClaimAuditService.gI().complete(player, period, tier, state);
            InventoryService.gI().sendItemBags(player);
            PlayerService.gI().sendInfoHpMpMoney(player);
            Service.gI().sendNangDong(player);
            return ActivityClaimResult.of(ActivityClaimResult.Reason.SUCCESS,
                    "Bạn đã nhận quà mốc " + tier.getThreshold() + " điểm Năng động.");
        }
    }

    static TierStatus evaluate(ActivityState state, ActivityPeriodType period, ActivityTier tier) {
        long mask = period == ActivityPeriodType.DAILY ? state.dailyClaimMask : state.weeklyClaimMask;
        boolean claimed = (mask & (1L << tier.getClaimBit())) != 0;
        int points = period == ActivityPeriodType.DAILY ? state.dailyPoints : state.weeklyPoints;
        boolean eligible = tier.isEnabled() && !claimed && points >= tier.getThreshold()
                && (period != ActivityPeriodType.WEEKLY || state.qualifiedDays >= tier.getMinQualifiedDays());
        return new TierStatus(tier, claimed, eligible, points, state.qualifiedDays);
    }

    public static String describeRewards(ActivityTier tier) {
        if (tier == null || tier.getRewards().isEmpty()) {
            return "Không có quà";
        }
        List<String> values = new ArrayList<>();
        for (ActivityReward reward : tier.getRewards()) {
            String amount = reward.getQuantityMin() == reward.getQuantityMax()
                    ? String.valueOf(reward.getQuantityMin())
                    : reward.getQuantityMin() + "-" + reward.getQuantityMax();
            switch (reward.getKind()) {
                case GOLD -> values.add(amount + " vàng");
                case GEM -> values.add(amount + " ngọc");
                case RUBY -> values.add(amount + " hồng ngọc");
                case ITEM -> values.add("vật phẩm #" + reward.getItemId() + " x" + amount);
                default -> { }
            }
        }
        return String.join(", ", values);
    }

    private RewardBundle buildBundle(Player player, ActivityTier tier) {
        RewardBundle bundle = new RewardBundle();
        for (ActivityReward reward : tier.getRewards()) {
            if (reward.getGender() >= 0 && reward.getGender() < 3 && reward.getGender() != player.gender) {
                bundle.failure = ActivityClaimResult.of(ActivityClaimResult.Reason.GENDER_MISMATCH,
                        "Gói quà này không áp dụng cho chủng tộc của nhân vật.");
                return bundle;
            }
            int amount = randomBetween(reward.getQuantityMin(), reward.getQuantityMax());
            try {
                switch (reward.getKind()) {
                    case GOLD -> {
                        bundle.gold = Math.addExact(bundle.gold, amount);
                        bundle.delivered.add(amount + " GOLD");
                    }
                    case GEM -> {
                        bundle.gem = Math.addExact(bundle.gem, amount);
                        bundle.delivered.add(amount + " GEM");
                    }
                    case RUBY -> {
                        bundle.ruby = Math.addExact(bundle.ruby, amount);
                        bundle.delivered.add(amount + " RUBY");
                    }
                    case ITEM -> addItemReward(bundle, reward, amount);
                    default -> throw new IllegalArgumentException("Unsupported reward kind");
                }
            } catch (Exception e) {
                bundle.failure = ActivityClaimResult.of(ActivityClaimResult.Reason.INVALID_REWARD,
                        "Cấu hình phần thưởng không hợp lệ. Quản trị viên cần kiểm tra lại mốc quà.");
                return bundle;
            }
        }
        return bundle;
    }

    private void addItemReward(RewardBundle bundle, ActivityReward reward, int amount) {
        Item probe = ItemService.gI().createNewItem((short) reward.getItemId(), amount);
        if (probe == null || probe.template == null || probe.template.type == 9
                || probe.template.type == 10 || probe.template.type == 34) {
            throw new IllegalArgumentException("Invalid item reward template");
        }
        List<Item.ItemOption> options = new ArrayList<>();
        for (ActivityRewardOption configured : reward.getOptions()) {
            Item.ItemOption option = new Item.ItemOption(configured.getOptionId(),
                    randomBetween(configured.getParamMin(), configured.getParamMax()));
            if (option.optionTemplate == null) {
                throw new IllegalArgumentException("Unknown item option");
            }
            options.add(option);
        }
        Item item = ItemService.gI().createNewItemWithOptions((short) reward.getItemId(), amount,
                reward.isUseDefaultOptions(), options);
        if (item == null || item.template == null) {
            throw new IllegalArgumentException("Unknown item template");
        }
        if (reward.isInitBaseOptions()) {
            RewardService.gI().initChiSoItem(item);
        }
        if (reward.isBound()) {
            replaceOption(item, 30, 0);
        }
        if (reward.getExpiryDaysMax() > 0) {
            replaceOption(item, 93, randomBetween(reward.getExpiryDaysMin(), reward.getExpiryDaysMax()));
        }
        item.info = item.getInfo();
        item.content = item.getContent();
        bundle.items.add(item);
        bundle.delivered.add("ITEM:" + reward.getItemId() + "x" + amount);
    }

    private void replaceOption(Item item, int optionId, int param) {
        item.itemOptions.removeIf(option -> option != null && option.optionTemplate != null
                && option.optionTemplate.id == optionId);
        Item.ItemOption option = new Item.ItemOption(optionId, param);
        if (option.optionTemplate == null) {
            throw new IllegalArgumentException("Unknown item option");
        }
        item.itemOptions.add(option);
    }

    private boolean wouldOverflowCurrency(Player player, RewardBundle bundle) {
        return bundle.gold > PlayerConfig.getMaxGold() - player.inventory.gold
                || bundle.gem > Integer.MAX_VALUE - (long) player.inventory.gem
                || bundle.ruby > Integer.MAX_VALUE - (long) player.inventory.ruby;
    }

    private List<Item> copyBag(Player player) {
        List<Item> backup = new ArrayList<>();
        for (Item current : player.inventory.itemsBag) {
            backup.add(current != null && current.isNotNullItem() ? ItemService.gI().copyItem(current) : new Item());
        }
        return backup;
    }

    private void restoreBag(Player player, List<Item> backup) {
        player.inventory.itemsBag.clear();
        player.inventory.itemsBag.addAll(backup);
    }

    private int randomBetween(int min, int max) {
        return min == max ? min : ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    private ActivityTier findTier(ActivityConfig config, ActivityPeriodType period, String tierId) {
        for (ActivityTier tier : tiers(config, period)) {
            if (tier.getId().equals(tierId)) {
                return tier;
            }
        }
        return null;
    }

    private List<ActivityTier> tiers(ActivityConfig config, ActivityPeriodType period) {
        return period == ActivityPeriodType.DAILY ? config.getDailyTiers() : config.getWeeklyTiers();
    }

    private void setClaimed(ActivityState state, ActivityPeriodType period, int claimBit) {
        if (period == ActivityPeriodType.DAILY) {
            state.dailyClaimMask |= 1L << claimBit;
        } else {
            state.weeklyClaimMask |= 1L << claimBit;
        }
    }

    public static final class TierStatus {
        private final ActivityTier tier;
        private final boolean claimed;
        private final boolean eligible;
        private final int currentPoints;
        private final int qualifiedDays;

        private TierStatus(ActivityTier tier, boolean claimed, boolean eligible,
                int currentPoints, int qualifiedDays) {
            this.tier = tier;
            this.claimed = claimed;
            this.eligible = eligible;
            this.currentPoints = currentPoints;
            this.qualifiedDays = qualifiedDays;
        }

        public ActivityTier getTier() { return tier; }
        public boolean isClaimed() { return claimed; }
        public boolean isEligible() { return eligible; }
        public int getCurrentPoints() { return currentPoints; }
        public int getQualifiedDays() { return qualifiedDays; }
    }

    private static final class RewardBundle {
        private final List<Item> items = new ArrayList<>();
        private final List<String> delivered = new ArrayList<>();
        private long gold;
        private long gem;
        private long ruby;
        private ActivityClaimResult failure;
    }
}
