package nro.models.player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import nro.models.consts.ConstTaskBadges;
import nro.models.item.Item;
import nro.models.item.Item.ItemOption;
import nro.models.task.BadgesTaskService;

/**
 *
 * @author By Mr Blue
 *
 */
public class Inventory {

    public Item trainArmor;
    public List<String> giftCode;
    public List<Item> itemsBody;
    public List<Item> itemsBag;
    public List<Item> itemsBox;

    public List<Item> itemsBoxCrackBall;
    public List<Item> itemsDaBan;

    public long gold;
    public int gem;
    public int ruby;
    public int coupon;
    public int event;
    public Iterable<Item> items;

    public Inventory() {
        itemsBody = new ArrayList<>();
        itemsBag = new ArrayList<>();
        itemsBox = new ArrayList<>();
        itemsBoxCrackBall = new ArrayList<>();
        itemsDaBan = new ArrayList<>();
        giftCode = new ArrayList<>();
    }

    public int getGem() {
        return this.gem;
    }

    public int getParam(Item it, int id) {
        for (ItemOption op : it.itemOptions) {
            if (op != null && op.optionTemplate.id == id) {
                return op.param;
            }
        }
        return 0;
    }

    public boolean haveOption(List<Item> l, int index, int id) {
        Item it = l.get(index);
        if (it != null && it.isNotNullItem()) {
            return it.itemOptions.stream().anyMatch(op -> op != null && op.optionTemplate.id == id);
        }
        return false;
    }

    public void subGem(int num) {
        this.gem -= num;
    }

    private volatile boolean active = true;

    public boolean isActive() {
        return this.active;
    }

    public enum GemCreditStatus {
        SUCCESS,
        GEM_LIMIT,
        INVALID_AMOUNT,
        INACTIVE,
        // [H] Corrupt/negative gem balance – credit rejected, state unchanged.
        INVALID_BALANCE
    }

    public static class GemCreditResult {
        public final GemCreditStatus status;
        public final int requestedAmount;
        public final int creditedAmount;
        public final int balanceBefore;
        public final int balanceAfter;

        public GemCreditResult(GemCreditStatus status, int requestedAmount, int creditedAmount, int balanceBefore, int balanceAfter) {
            this.status = status;
            this.requestedAmount = requestedAmount;
            this.creditedAmount = creditedAmount;
            this.balanceBefore = balanceBefore;
            this.balanceAfter = balanceAfter;
        }

        public boolean isSuccess() {
            return status == GemCreditStatus.SUCCESS;
        }
    }

    public synchronized GemCreditResult tryCreditGemExact(int amount) {
        if (!this.active) {
            return new GemCreditResult(GemCreditStatus.INACTIVE, amount, 0, this.gem, this.gem);
        }
        if (amount <= 0) {
            return new GemCreditResult(GemCreditStatus.INVALID_AMOUNT, amount, 0, this.gem, this.gem);
        }
        int max = PlayerConfig.getMaxGem();
        if (this.gem < 0 || this.gem > max) {
            nro.models.utils.Logger.error("[Inventory] Corrupt gem balance=" + this.gem
                    + " for credit request=" + amount + " – rejecting with INVALID_BALANCE");
            return new GemCreditResult(GemCreditStatus.INVALID_BALANCE, amount, 0, this.gem, this.gem);
        }
        long current = (long) this.gem;
        long newBalance = current + (long) amount;
        if (newBalance > max) {
            return new GemCreditResult(GemCreditStatus.GEM_LIMIT, amount, 0, this.gem, this.gem);
        }
        int before = this.gem;
        this.gem = (int) newBalance;
        return new GemCreditResult(GemCreditStatus.SUCCESS, amount, amount, before, this.gem);
    }

    public synchronized void addGem(int gem) {
        if (gem <= 0 || !this.active) {
            return;
        }
        long max = PlayerConfig.getMaxGem();
        if (this.gem < 0 || this.gem > max) {
            nro.models.utils.Logger.error("[Inventory] Corrupt gem balance=" + this.gem
                    + " for addGem request=" + gem + " – credit ignored");
            return;
        }
        long updated = Math.min((long) this.gem + (long) gem, max);
        this.gem = (int) updated;
    }

    public void subGold(int num) {
        this.gold -= num;
    }

    public void addGold(int gold) {
        this.gold += gold;
        if (this.gold > PlayerConfig.getMaxGold()) {
            this.gold = PlayerConfig.getMaxGold();
        }
    }

    // [I] dispose() synchronized on same monitor as tryCreditGemExact/addGem so
    //     active=false is visible to any concurrent credit attempt immediately.
    public synchronized void dispose() {
        this.active = false;
        if (this.trainArmor != null) {
            this.trainArmor.dispose();
        }
        this.trainArmor = null;
        if (this.itemsBody != null) {
            for (Item it : this.itemsBody) {
                it.dispose();
            }
            this.itemsBody.clear();
        }
        if (this.itemsBag != null) {
            for (Item it : this.itemsBag) {
                it.dispose();
            }
            this.itemsBag.clear();
        }
        if (this.itemsBox != null) {
            for (Item it : this.itemsBox) {
                it.dispose();
            }
            this.itemsBox.clear();
        }
        if (this.itemsBoxCrackBall != null) {
            for (Item it : this.itemsBoxCrackBall) {
                it.dispose();
            }
            this.itemsBoxCrackBall.clear();
        }
        if (this.itemsDaBan != null) {
            for (Item it : this.itemsDaBan) {
                it.dispose();
            }
            this.itemsDaBan.clear();
        }
        this.itemsBody = null;
        this.itemsBag = null;
        this.itemsBox = null;
        this.itemsBoxCrackBall = null;
        this.itemsDaBan = null;
    }


    public void checkAndUpdateMeRongBadges(Player player) {
        if (player == null) {
            return;
        }
        Set<Integer> checkedItemIds = new HashSet<>();

        List<List<Item>> inventories = Arrays.asList(
                this.itemsBag,
                this.itemsBox,
                this.itemsBody
        );

        for (List<Item> inventory : inventories) {
            if (inventory == null) {
                continue;
            }
            for (Item item : inventory) {
                if (item != null && item.template != null && isPermanent(item)) {
                    int itemId = item.template.id;
                    if (itemId >= 1765 && itemId <= 1771 && !checkedItemIds.contains(itemId)) {
                        checkedItemIds.add(itemId);
                    }
                }
            }
        }
        BadgesTaskService.setCountBadgesTask(player, ConstTaskBadges.ME_RONG, checkedItemIds.size());
    }

    private boolean isPermanent(Item item) {
        return item != null && item.getOptionById(93) == null;
    }
}
