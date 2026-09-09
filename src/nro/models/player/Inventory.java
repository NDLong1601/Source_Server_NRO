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

    private final PlayerWallet wallet;
    private volatile Runnable mutationListener = () -> { };

    public Inventory() {
        this.wallet = new PlayerWallet(this);
        itemsBody = new ArrayList<>();
        itemsBag = new ArrayList<>();
        itemsBox = new ArrayList<>();
        itemsBoxCrackBall = new ArrayList<>();
        itemsDaBan = new ArrayList<>();
        giftCode = new ArrayList<>();
    }

    public PlayerWallet getWallet() {
        return this.wallet;
    }

    public void setMutationListener(Runnable listener) {
        this.mutationListener = listener == null ? () -> { } : listener;
    }

    void markChanged() {
        this.mutationListener.run();
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

    @Deprecated
    public void subGem(int num) {
        this.wallet.tryDebit(Currency.GEM, num,
                WalletMutationContext.of(WalletReason.OTHER)).requireSuccess();
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

    @Deprecated
    public synchronized GemCreditResult tryCreditGemExact(int amount) {
        WalletResult result = this.wallet.tryCreditExact(Currency.GEM, amount,
                WalletMutationContext.of(WalletReason.ACHIEVEMENT_REWARD));
        GemCreditStatus status = switch (result.getStatus()) {
            case SUCCESS -> GemCreditStatus.SUCCESS;
            case INACTIVE -> GemCreditStatus.INACTIVE;
            case INVALID_AMOUNT -> GemCreditStatus.INVALID_AMOUNT;
            case INVALID_BALANCE -> GemCreditStatus.INVALID_BALANCE;
            case LIMIT_EXCEEDED -> GemCreditStatus.GEM_LIMIT;
            default -> GemCreditStatus.INVALID_AMOUNT;
        };
        return new GemCreditResult(status, (int) result.getAmountRequested(),
                (int) result.getAmountApplied(), (int) result.getBalanceBefore(), (int) result.getBalanceAfter());
    }

    @Deprecated
    public synchronized void addGem(int gem) {
        this.wallet.creditUpToCap(Currency.GEM, gem,
                WalletMutationContext.of(WalletReason.OTHER)).requireSuccess();
    }

    @Deprecated
    public void subGold(int num) {
        this.wallet.tryDebit(Currency.GOLD, num,
                WalletMutationContext.of(WalletReason.OTHER)).requireSuccess();
    }

    @Deprecated
    public void addGold(int gold) {
        this.wallet.creditUpToCap(Currency.GOLD, gold,
                WalletMutationContext.of(WalletReason.OTHER)).requireSuccess();
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
