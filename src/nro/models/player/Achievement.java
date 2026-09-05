package nro.models.player;

import nro.models.consts.ConstAchievement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import nro.models.item.Item;
import nro.models.player_system.Template.AchievementQuest;
import nro.models.player_system.Template.AchievementTemplate;
import nro.models.server.Manager;
import nro.models.utils.Logger;

public class Achievement {

    public enum LifecycleState {
        ACTIVE,
        CLOSED
    }

    public enum ClaimStatus {
        SUCCESS,
        INVALID_INDEX,
        NOT_COMPLETED,
        ALREADY_CLAIMED,
        BAG_FULL,
        PLAYER_NULL,
        GEM_LIMIT,
        INVALID_BALANCE,
        PLAYER_UNAVAILABLE
    }

    public static class ClaimResult {

        private final ClaimStatus status;
        private final int rewardGem;
        private final int totalGem;

        public ClaimResult(ClaimStatus status, int rewardGem, int totalGem) {
            this.status = status;
            this.rewardGem = rewardGem;
            this.totalGem = totalGem;
        }

        public static ClaimResult success(int rewardGem, int totalGem) {
            return new ClaimResult(ClaimStatus.SUCCESS, rewardGem, totalGem);
        }

        public static ClaimResult failure(ClaimStatus status) {
            return new ClaimResult(status, 0, 0);
        }

        public ClaimStatus getStatus() {
            return status;
        }

        public int getRewardGem() {
            return rewardGem;
        }

        public int getTotalGem() {
            return totalGem;
        }

        public boolean isSuccess() {
            return status == ClaimStatus.SUCCESS;
        }
    }

    private final Object lock = new Object();

    private Player player;
    private LifecycleState lifecycleState = LifecycleState.ACTIVE;

    private List<AchievementQuest> achievementList;

    public Achievement(Player player) {
        this.player = player;
        this.achievementList = new ArrayList<>();
    }

    public void closeClaims() {
        synchronized (lock) {
            this.lifecycleState = LifecycleState.CLOSED;
        }
    }

    public boolean isAcceptingClaims() {
        synchronized (lock) {
            return this.lifecycleState == LifecycleState.ACTIVE;
        }
    }

    /**
     * Returns an immutable defensive snapshot of the achievement list.
     * Index positions are preserved: null elements become placeholder quests (completed=0, isRecieve=false).
     * Modifications to the returned list do NOT affect internal state.
     */
    public List<AchievementQuest> getAchievementList() {
        return Collections.unmodifiableList(snapshotQuests());
    }

    public List<AchievementQuest> snapshotQuests() {
        synchronized (lock) {
            if (achievementList == null) {
                return new ArrayList<>();
            }
            List<AchievementQuest> copy = new ArrayList<>(achievementList.size());
            for (int i = 0; i < achievementList.size(); i++) {
                AchievementQuest aq = achievementList.get(i);
                if (aq != null) {
                    copy.add(new AchievementQuest(aq.completed, aq.isRecieve));
                } else {
                    Logger.error("[Achievement] snapshotQuests: null element at index=" + i + " – using placeholder");
                    copy.add(new AchievementQuest(0, false));
                }
            }
            return copy;
        }
    }

    public void add(AchievementQuest achievement) {
        synchronized (lock) {
            if (lifecycleState == LifecycleState.ACTIVE && achievementList != null) {
                achievementList.add(copyOf(achievement));
            }
        }
    }

    /**
     * Returns a defensive copy. Mutating it never changes the live achievement state.
     */
    public AchievementQuest get(int index) {
        synchronized (lock) {
            return copyOf(getQuestLocked(index));
        }
    }

    public long getCompleted(int index) {
        synchronized (lock) {
            return getCompletedLocked(index);
        }
    }

    public boolean isFinish(int index, long maxCount) {
        synchronized (lock) {
            AchievementQuest aq = getQuestLocked(index);
            return aq != null && (aq.isRecieve || getCompletedLocked(index) >= maxCount);
        }
    }

    public boolean isRecieve(int index) {
        synchronized (lock) {
            AchievementQuest aq = getQuestLocked(index);
            return aq != null && aq.isRecieve;
        }
    }

    public boolean canReward(int index) {
        synchronized (lock) {
            if (lifecycleState != LifecycleState.ACTIVE
                    || index < 0 || index >= Manager.ACHIEVEMENT_TEMPLATE.size()) {
                return false;
            }
            AchievementQuest aq = getQuestLocked(index);
            AchievementTemplate at = Manager.ACHIEVEMENT_TEMPLATE.get(index);
            return aq != null && at != null && !aq.isRecieve
                    && getCompletedLocked(index) >= at.maxCount;
        }
    }

    public ClaimResult claimReward(int index) {
        return claimReward(index, false);
    }

    public ClaimResult claimReward(int index, boolean requireEmptyBagSlot) {
        if (player == null) {
            return ClaimResult.failure(ClaimStatus.PLAYER_NULL);
        }
        synchronized (lock) {
            if (lifecycleState != LifecycleState.ACTIVE) {
                return ClaimResult.failure(ClaimStatus.PLAYER_UNAVAILABLE);
            }
            if (achievementList == null) {
                return ClaimResult.failure(ClaimStatus.INVALID_INDEX);
            }
            if (index < 0 || index >= achievementList.size() || index >= Manager.ACHIEVEMENT_TEMPLATE.size()) {
                return ClaimResult.failure(ClaimStatus.INVALID_INDEX);
            }
            AchievementQuest aq = achievementList.get(index);
            AchievementTemplate at = Manager.ACHIEVEMENT_TEMPLATE.get(index);
            if (aq == null || at == null) {
                return ClaimResult.failure(ClaimStatus.INVALID_INDEX);
            }
            if (aq.isRecieve) {
                return ClaimResult.failure(ClaimStatus.ALREADY_CLAIMED);
            }
            if (getCompletedLocked(index) < at.maxCount) {
                return ClaimResult.failure(ClaimStatus.NOT_COMPLETED);
            }
            if (requireEmptyBagSlot && !hasEmptyBagSlot()) {
                return ClaimResult.failure(ClaimStatus.BAG_FULL);
            }
            if (player.inventory == null) {
                return ClaimResult.failure(ClaimStatus.PLAYER_UNAVAILABLE);
            }

            Inventory.GemCreditResult creditResult = player.inventory.tryCreditGemExact(at.money);
            if (creditResult.status == Inventory.GemCreditStatus.GEM_LIMIT) {
                return ClaimResult.failure(ClaimStatus.GEM_LIMIT);
            }
            if (creditResult.status == Inventory.GemCreditStatus.INVALID_BALANCE) {
                return ClaimResult.failure(ClaimStatus.INVALID_BALANCE);
            }
            if (!creditResult.isSuccess()) {
                return ClaimResult.failure(ClaimStatus.PLAYER_UNAVAILABLE);
            }

            aq.isRecieve = true;
            achievementList.set(index, new AchievementQuest(aq.completed, true));
            return ClaimResult.success(creditResult.creditedAmount, creditResult.balanceAfter);
        }
    }

    private boolean hasEmptyBagSlot() {
        if (player == null || player.inventory == null || player.inventory.itemsBag == null) {
            return false;
        }
        for (Item item : player.inventory.itemsBag) {
            if (item == null || !item.isNotNullItem()) {
                return true;
            }
        }
        return false;
    }

    public void done(int index, long completed) {
        synchronized (lock) {
            if (lifecycleState != LifecycleState.ACTIVE) {
                return;
            }
            if (achievementList != null && index >= 0 && index < achievementList.size()) {
                AchievementQuest aq = achievementList.get(index);
                if (aq != null) {
                    achievementList.set(index, new AchievementQuest(aq.completed + completed, aq.isRecieve));
                }
            }
        }
    }

    public void doneNotAdd(int index, long completed) {
        synchronized (lock) {
            if (lifecycleState != LifecycleState.ACTIVE) {
                return;
            }
            if (achievementList != null && index >= 0 && index < achievementList.size()) {
                AchievementQuest aq = achievementList.get(index);
                if (aq != null) {
                    achievementList.set(index, new AchievementQuest(completed, aq.isRecieve));
                }
            }
        }
    }

    /**
     * @deprecated Legacy method – has zero production callers and cannot safely
     * credit gems. Calling this method is a bug; use {@link #claimReward(int)} instead.
     * This method is a deliberate no-op so that any accidental call is harmless.
     */
    @Deprecated
    public void reward(int index) {
        Logger.error("[Achievement] reward() called on index=" + index
                + " – this is a bug; use claimReward(). No state was changed.");
    }

    public void dispose() {
        synchronized (lock) {
            this.lifecycleState = LifecycleState.CLOSED;
            if (achievementList != null) {
                achievementList.clear();
                achievementList = null;
            }
            player = null;
        }
    }

    private AchievementQuest getQuestLocked(int index) {
        return achievementList != null && index >= 0 && index < achievementList.size()
                ? achievementList.get(index) : null;
    }

    private long getCompletedLocked(int index) {
        AchievementQuest aq = getQuestLocked(index);
        if (aq == null) {
            return 0;
        }
        if (lifecycleState == LifecycleState.ACTIVE && player != null) {
            switch (index) {
                case 0, 1, 16 -> {
                    if (player.nPoint != null) {
                        aq.completed = player.nPoint.power;
                    }
                }
                case 2 -> {
                    if (player.magicTree != null) {
                        aq.completed = player.magicTree.level;
                    }
                }
                default -> {
                }
            }
        }
        return index == ConstAchievement.HOAT_DONG_CHAM_CHI
                ? aq.completed / (60 * 60 * 1000) : aq.completed;
    }

    private AchievementQuest copyOf(AchievementQuest achievement) {
        return achievement == null ? null
                : new AchievementQuest(achievement.completed, achievement.isRecieve);
    }

}
