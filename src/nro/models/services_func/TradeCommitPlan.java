package nro.models.services_func;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import nro.models.item.Item;
import nro.models.player.Player;
import nro.models.player.PlayerConfig;
import nro.models.services.InventoryService;
import nro.models.services.ItemService;
import nro.models.utils.Logger;

/** SEC-03 exact-source, ordered-lock, delta-only trade commit. */
public final class TradeCommitPlan {

    private TradeCommitPlan() {
    }

    public static final class CommitResult {
        public final boolean success;
        public final byte status;
        public final String failureMessage;
        public final ImmutableTradeData tradeData;

        private CommitResult(boolean success, byte status, String failureMessage, ImmutableTradeData tradeData) {
            this.success = success;
            this.status = status;
            this.failureMessage = failureMessage;
            this.tradeData = tradeData;
        }

        static CommitResult success(ImmutableTradeData data) {
            return new CommitResult(true, Trade.SUCCESS, "Giao dịch thành công", data);
        }

        static CommitResult fail(byte status, String failureMessage) {
            return new CommitResult(false, status, failureMessage, null);
        }
    }

    /** Deep immutable audit snapshot; no live Item reference escapes into the DAO. */
    public static final class ImmutableTradeData {
        public final String tradeId;
        public final long player1Id;
        public final long player2Id;
        public final String player1Name;
        public final String player2Name;
        public final int goldTrade1;
        public final int goldTrade2;
        public final long gold1Before;
        public final long gold2Before;
        public final long gold1After;
        public final long gold2After;
        public final List<Item> itemsTrade1;
        public final List<Item> itemsTrade2;
        public final List<Item> bag1Before;
        public final List<Item> bag2Before;
        public final List<Item> bag1After;
        public final List<Item> bag2After;

        public ImmutableTradeData(String tradeId, long p1Id, long p2Id, String p1Name, String p2Name,
                                  int goldTrade1, int goldTrade2,
                                  long gold1Before, long gold2Before, long gold1After, long gold2After,
                                  List<Item> itemsTrade1, List<Item> itemsTrade2,
                                  List<Item> bag1Before, List<Item> bag2Before,
                                  List<Item> bag1After, List<Item> bag2After) {
            this.tradeId = tradeId;
            this.player1Id = p1Id;
            this.player2Id = p2Id;
            this.player1Name = p1Name;
            this.player2Name = p2Name;
            this.goldTrade1 = goldTrade1;
            this.goldTrade2 = goldTrade2;
            this.gold1Before = gold1Before;
            this.gold2Before = gold2Before;
            this.gold1After = gold1After;
            this.gold2After = gold2After;
            this.itemsTrade1 = deepImmutable(itemsTrade1);
            this.itemsTrade2 = deepImmutable(itemsTrade2);
            this.bag1Before = deepImmutable(bag1Before);
            this.bag2Before = deepImmutable(bag2Before);
            this.bag1After = deepImmutable(bag1After);
            this.bag2After = deepImmutable(bag2After);
        }
    }

    private static final class Debit {
        final Trade.SourceItemRef ref;
        final int liveIndex;
        long quantity;

        Debit(Trade.SourceItemRef ref, int liveIndex) {
            this.ref = ref;
            this.liveIndex = liveIndex;
        }
    }

    /** Snapshot used for COMMITTING and non-commit terminal ledger records. */
    public static ImmutableTradeData snapshot(Trade trade) {
        Player p1 = trade.getPlayer1();
        Player p2 = trade.getPlayer2();
        return withOrderedInventoryLocks(p1, p2, () -> {
            List<Item> bag1 = copyBag(p1);
            List<Item> bag2 = p2.isBot ? new ArrayList<>() : copyBag(p2);
            long gold1 = p1.inventory != null ? p1.inventory.gold : 0;
            long gold2 = !p2.isBot && p2.inventory != null ? p2.inventory.gold : 0;
            return new ImmutableTradeData(trade.getTradeId(), p1.id, p2.id, p1.name, p2.name,
                    trade.getGoldTrade1(), trade.getGoldTrade2(), gold1, gold2, gold1, gold2,
                    trade.getOfferItems1Snapshot(), trade.getOfferItems2Snapshot(),
                    bag1, bag2, bag1, bag2);
        });
    }

    public static CommitResult execute(Trade trade) {
        Player p1 = trade.getPlayer1();
        Player p2 = trade.getPlayer2();
        if (p1 == null || p2 == null) {
            return CommitResult.fail(Trade.FAIL_ACTIVE, "Người chơi không hợp lệ");
        }
        return withOrderedInventoryLocks(p1, p2, () -> executeUnderLocks(trade, p1, p2));
    }

    private static CommitResult executeUnderLocks(Trade trade, Player p1, Player p2) {
        if (p1.inventory == null || !p1.inventory.isActive()) {
            return CommitResult.fail(Trade.FAIL_ACTIVE, "Người chơi không hoạt động");
        }
        if (!p2.isBot && (p2.inventory == null || !p2.inventory.isActive())) {
            return CommitResult.fail(Trade.FAIL_ACTIVE, "Đối phương không hoạt động");
        }

        int goldTrade1 = trade.getGoldTrade1();
        int goldTrade2 = trade.getGoldTrade2();
        if (goldTrade1 < 0 || goldTrade1 > Trade.MAX_GOLD_TRADE_PER_TIME
                || goldTrade2 < 0 || goldTrade2 > Trade.MAX_GOLD_TRADE_PER_TIME) {
            return CommitResult.fail(Trade.FAIL_INVALID_OFFER, "Số vàng giao dịch không hợp lệ");
        }

        long p1Gold = p1.inventory.gold;
        long p2Gold = p2.isBot ? 0 : p2.inventory.gold;
        long maxGold = PlayerConfig.getMaxGold();
        if (p1Gold < 0 || p1Gold > maxGold || p1Gold < goldTrade1
                || (!p2.isBot && (p2Gold < 0 || p2Gold > maxGold || p2Gold < goldTrade2))) {
            return CommitResult.fail(Trade.FAIL_INVALID_OFFER, "Không đủ vàng để giao dịch");
        }
        long p1GoldAfter = p1Gold - goldTrade1 + goldTrade2;
        long p2GoldAfter = p2Gold - goldTrade2 + goldTrade1;
        if (p1GoldAfter < 0 || p1GoldAfter > maxGold) {
            return CommitResult.fail(Trade.FAIL_MAX_GOLD_PLAYER1, "Vàng người chơi 1 vượt giới hạn");
        }
        if (!p2.isBot && (p2GoldAfter < 0 || p2GoldAfter > maxGold)) {
            return CommitResult.fail(Trade.FAIL_MAX_GOLD_PLAYER2, "Vàng người chơi 2 vượt giới hạn");
        }

        List<Item> offers1 = trade.getOfferItems1Snapshot();
        List<Item> offers2 = trade.getOfferItems2Snapshot();
        List<Debit> debits1 = resolveDebits(trade, p1, offers1);
        if (debits1 == null) {
            return CommitResult.fail(Trade.FAIL_INVALID_OFFER, "Vật phẩm người chơi 1 không còn đúng định danh hoặc số lượng");
        }
        List<Debit> debits2 = p2.isBot ? new ArrayList<>() : resolveDebits(trade, p2, offers2);
        if (debits2 == null) {
            return CommitResult.fail(Trade.FAIL_INVALID_OFFER, "Vật phẩm người chơi 2 không còn đúng định danh hoặc số lượng");
        }
        if (p2.isBot && !validBotOffers(offers2)) {
            return CommitResult.fail(Trade.FAIL_INVALID_OFFER, "Vật phẩm bot không hợp lệ");
        }

        List<Item> transfers1 = createTransferItems(offers1);
        List<Item> transfers2 = createTransferItems(offers2);
        if (transfers1 == null || transfers2 == null) {
            return CommitResult.fail(Trade.FAIL_TRADE_LIMIT, "Vật phẩm đã hết số lần giao dịch");
        }

        List<Item> simBag1 = InventoryService.gI().copyList(p1.inventory.itemsBag);
        List<Item> simBag2 = p2.isBot ? new ArrayList<>() : InventoryService.gI().copyList(p2.inventory.itemsBag);
        applyDebitsToSimulation(simBag1, debits1);
        if (!p2.isBot) {
            applyDebitsToSimulation(simBag2, debits2);
        }
        if (!p2.isBot && !simulateCredits(simBag2, transfers1)) {
            return CommitResult.fail(Trade.FAIL_NOT_ENOUGH_BAG_P2, "Đối phương không đủ chỗ chứa");
        }
        if (!simulateCredits(simBag1, transfers2)) {
            return CommitResult.fail(Trade.FAIL_NOT_ENOUGH_BAG_P1, "Hành trang bạn không đủ chỗ chứa");
        }

        List<Item> bag1Before = InventoryService.gI().copyList(p1.inventory.itemsBag);
        List<Item> bag2Before = p2.isBot ? new ArrayList<>() : InventoryService.gI().copyList(p2.inventory.itemsBag);
        try {
            applyLiveDebits(p1.inventory.itemsBag, debits1);
            if (!p2.isBot) {
                applyLiveDebits(p2.inventory.itemsBag, debits2);
            }
            if (!p2.isBot) {
                applyLiveCredits(p2.inventory.itemsBag, transfers1);
            }
            applyLiveCredits(p1.inventory.itemsBag, transfers2);
            p1.inventory.gold = p1GoldAfter;
            if (!p2.isBot) {
                p2.inventory.gold = p2GoldAfter;
            }
        } catch (RuntimeException ex) {
            restoreBag(p1.inventory.itemsBag, bag1Before);
            p1.inventory.gold = p1Gold;
            if (!p2.isBot) {
                restoreBag(p2.inventory.itemsBag, bag2Before);
                p2.inventory.gold = p2Gold;
            }
            Logger.logException(TradeCommitPlan.class,
                    new Exception("Rolled back unexpected trade apply failure tradeId=" + trade.getTradeId(), ex));
            return CommitResult.fail(Trade.FAIL_INVALID_OFFER, "Không thể áp dụng thay đổi giao dịch");
        }

        List<Item> bag1After = InventoryService.gI().copyList(p1.inventory.itemsBag);
        List<Item> bag2After = p2.isBot ? new ArrayList<>() : InventoryService.gI().copyList(p2.inventory.itemsBag);
        ImmutableTradeData data = new ImmutableTradeData(trade.getTradeId(), p1.id, p2.id,
                p1.name, p2.name, goldTrade1, goldTrade2,
                p1Gold, p2Gold, p1GoldAfter, p2GoldAfter,
                offers1, offers2, bag1Before, bag2Before, bag1After, bag2After);
        return CommitResult.success(data);
    }

    private static List<Debit> resolveDebits(Trade trade, Player owner, List<Item> offers) {
        if (owner.inventory == null || owner.inventory.itemsBag == null) {
            return null;
        }
        Map<Trade.SourceItemRef, Debit> aggregated = new IdentityHashMap<>();
        for (Item offer : offers) {
            if (offer == null || !offer.isNotNullItem() || offer.quantity <= 0) {
                return null;
            }
            Trade.SourceItemRef ref = trade.getSourceRef(owner, offer);
            if (ref == null || !ref.matches(ref.source) || Trade.isItemCannotTran(ref.source)) {
                return null;
            }
            int liveIndex = identityIndexOf(owner.inventory.itemsBag, ref.source);
            if (liveIndex < 0) {
                return null;
            }
            Debit debit = aggregated.get(ref);
            if (debit == null) {
                debit = new Debit(ref, liveIndex);
                aggregated.put(ref, debit);
            } else if (debit.liveIndex != liveIndex) {
                return null;
            }
            debit.quantity += offer.quantity;
            if (debit.quantity > Integer.MAX_VALUE || debit.quantity > ref.source.quantity) {
                return null;
            }
        }
        return new ArrayList<>(aggregated.values());
    }

    private static boolean validBotOffers(List<Item> offers) {
        for (Item offer : offers) {
            if (offer == null || !offer.isNotNullItem() || offer.quantity <= 0) {
                return false;
            }
        }
        return true;
    }

    private static List<Item> createTransferItems(List<Item> offers) {
        List<Item> result = new ArrayList<>();
        for (Item offer : offers) {
            Item copy = ItemService.gI().copyItem(offer);
            Item.ItemOption limit = copy.getOptionById(232);
            if (limit != null) {
                if (limit.param <= 0) {
                    return null;
                }
                limit.param--;
            }
            result.add(copy);
        }
        return result;
    }

    private static void applyDebitsToSimulation(List<Item> bag, List<Debit> debits) {
        for (Debit debit : debits) {
            Item item = bag.get(debit.liveIndex);
            item.quantity -= (int) debit.quantity;
            if (item.quantity <= 0) {
                bag.set(debit.liveIndex, ItemService.gI().createItemNull());
            } else {
                syncQuantityOption(item);
            }
        }
    }

    private static boolean simulateCredits(List<Item> bag, List<Item> transfers) {
        for (Item transfer : transfers) {
            if (!InventoryService.gI().addItemList(bag, ItemService.gI().copyItem(transfer))) {
                return false;
            }
        }
        return true;
    }

    private static void applyLiveDebits(List<Item> bag, List<Debit> debits) {
        for (Debit debit : debits) {
            Item live = bag.get(debit.liveIndex);
            if (live != debit.ref.source || live.quantity < debit.quantity) {
                throw new IllegalStateException("Source item changed after validation");
            }
            live.quantity -= (int) debit.quantity;
            if (live.quantity == 0) {
                bag.set(debit.liveIndex, ItemService.gI().createItemNull());
            } else {
                syncQuantityOption(live);
            }
        }
    }

    private static void applyLiveCredits(List<Item> bag, List<Item> transfers) {
        for (Item transfer : transfers) {
            if (!InventoryService.gI().addItemList(bag, ItemService.gI().copyItem(transfer))) {
                throw new IllegalStateException("Destination changed after capacity simulation");
            }
        }
    }

    private static void restoreBag(List<Item> live, List<Item> snapshot) {
        live.clear();
        live.addAll(InventoryService.gI().copyList(snapshot));
    }

    private static void syncQuantityOption(Item item) {
        if (item.itemOptions == null) {
            return;
        }
        for (Item.ItemOption option : item.itemOptions) {
            if (option != null && option.optionTemplate != null && option.optionTemplate.id == 31) {
                option.param = Math.max(0, item.quantity);
                return;
            }
        }
    }

    private static int identityIndexOf(List<Item> bag, Item source) {
        for (int i = 0; i < bag.size(); i++) {
            if (bag.get(i) == source) {
                return i;
            }
        }
        return -1;
    }

    private static List<Item> copyBag(Player player) {
        if (player == null || player.inventory == null || player.inventory.itemsBag == null) {
            return new ArrayList<>();
        }
        return InventoryService.gI().copyList(player.inventory.itemsBag);
    }

    private static List<Item> deepImmutable(List<Item> source) {
        if (source == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(InventoryService.gI().copyList(source));
    }

    private interface LockedSupplier<T> {
        T get();
    }

    private static <T> T withOrderedInventoryLocks(Player p1, Player p2, LockedSupplier<T> supplier) {
        Object p1Lock = p1 != null && p1.inventory != null ? p1.inventory : p1;
        Object p2Lock = p2 != null && p2.inventory != null ? p2.inventory : p2;
        if (p1Lock == null || p2Lock == null) {
            return supplier.get();
        }
        Object first = p1.id < p2.id ? p1Lock : p2Lock;
        Object second = p1.id < p2.id ? p2Lock : p1Lock;
        synchronized (first) {
            synchronized (second) {
                return supplier.get();
            }
        }
    }
}
