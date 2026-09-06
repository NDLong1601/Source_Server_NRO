package nro.models.services_func;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import nro.models.consts.ConstItem;
import nro.models.item.Item;
import nro.models.item.Item.ItemOption;
import nro.models.network.Message;
import nro.models.player.Player;
import nro.models.player.PlayerConfig;
import nro.models.player_system.Template;
import nro.models.services.InventoryService;
import nro.models.services.ItemService;
import nro.models.services.RewardService;
import nro.models.services.Service;
import nro.models.utils.Logger;

public class LuckyRound {

    public static final byte MAX_ITEM_IN_BOX = 100;
    public static final byte USING_GEM = 7;
    public static final byte USING_GOLD = 0;

    public static final byte PRICE_GEM = 4;
    public static final int PRICE_GOLD = 250000000;

    public static final int MIN_ALLOWED_COUNT = 1;
    public static final int MAX_ALLOWED_COUNT = 7;

    private static LuckyRound instance;

    public static LuckyRound gI() {
        if (instance == null) {
            instance = new LuckyRound();
        }
        return instance;
    }

    public enum SpinStatus {
        SUCCESS,
        REOPEN_SUCCESS,
        MALFORMED_PACKET,
        INVALID_ACTION,
        INVALID_COUNT,
        INVALID_CURRENCY,
        UNSUPPORTED_CURRENCY,
        INSUFFICIENT_FUNDS,
        INVALID_BALANCE,
        INVALID_TICKET,
        BOX_FULL,
        INVENTORY_UNAVAILABLE,
        REWARD_GENERATION_FAILED,
        STATE_CHANGED,
        COMMIT_FAILED
    }

    public static class SpinResult {
        public final SpinStatus status;
        public final int action;
        public final int requestCount;
        public final byte currencyType;
        public final long requestedCost;
        public final long debitedAmount;
        public final long balanceBefore;
        public final long balanceAfter;
        public final int boxSizeBefore;
        public final int boxSizeAfter;
        public final int rewardsGenerated;
        public final List<Item> rewards;
        public final int ticketDebited;
        public final int paidCount;

        public SpinResult(SpinStatus status, int action, int requestCount, byte currencyType,
                          long requestedCost, long debitedAmount,
                          long balanceBefore, long balanceAfter,
                          int boxSizeBefore, int boxSizeAfter,
                          int rewardsGenerated, List<Item> rewards,
                          int ticketDebited, int paidCount) {
            this.status = status;
            this.action = action;
            this.requestCount = requestCount;
            this.currencyType = currencyType;
            this.requestedCost = requestedCost;
            this.debitedAmount = debitedAmount;
            this.balanceBefore = balanceBefore;
            this.balanceAfter = balanceAfter;
            this.boxSizeBefore = boxSizeBefore;
            this.boxSizeAfter = boxSizeAfter;
            this.rewardsGenerated = rewardsGenerated;
            this.rewards = rewards;
            this.ticketDebited = ticketDebited;
            this.paidCount = paidCount;
        }

        public boolean isSuccess() {
            return status == SpinStatus.SUCCESS;
        }

        public SpinStatus getStatus() {
            return status;
        }

        public int getRequestCount() {
            return requestCount;
        }

        public long getDebitedAmount() {
            return debitedAmount;
        }

        public static SpinResult failure(SpinStatus status, int action, int requestCount, byte currencyType) {
            return new SpinResult(status, action, requestCount, currencyType, 0L, 0L, 0L, 0L, 0, 0, 0, null, 0, 0);
        }
    }

    // Deep snapshot of an Item for rollback purposes
    private static final class ItemSnapshot {
        final Template.ItemTemplate template;
        final int quantity;
        final int quantityGD;
        final String info;
        final String content;
        final long createTime;
        final int id;
        final long expire;
        final List<ItemOption> itemOptions;

        ItemSnapshot(Item item) {
            this.template = item.template;
            this.quantity = item.quantity;
            this.quantityGD = item.quantityGD;
            this.info = item.info;
            this.content = item.content;
            this.createTime = item.createTime;
            this.id = item.id;
            this.expire = item.expire;
            this.itemOptions = new ArrayList<>();
            if (item.itemOptions != null) {
                for (ItemOption opt : item.itemOptions) {
                    if (opt == null) {
                        this.itemOptions.add(null);
                        continue;
                    }
                    ItemOption copy = new ItemOption();
                    copy.param = opt.param;
                    copy.optionTemplate = opt.optionTemplate;
                    this.itemOptions.add(copy);
                }
            }
        }

        void restore(Item item) {
            item.template = this.template;
            item.quantity = this.quantity;
            item.quantityGD = this.quantityGD;
            item.info = this.info;
            item.content = this.content;
            item.createTime = this.createTime;
            item.id = this.id;
            item.expire = this.expire;
            item.itemOptions = new ArrayList<>();
            for (ItemOption opt : this.itemOptions) {
                if (opt == null) {
                    item.itemOptions.add(null);
                    continue;
                }
                ItemOption copy = new ItemOption();
                copy.param = opt.param;
                copy.optionTemplate = opt.optionTemplate;
                item.itemOptions.add(copy);
            }
        }
    }

    private static void syncQuantityOption(Item item) {
        if (item == null || item.itemOptions == null) {
            return;
        }
        for (ItemOption option : item.itemOptions) {
            if (option != null && option.optionTemplate != null && option.optionTemplate.id == 31) {
                option.param = Math.max(0, item.quantity);
            }
        }
    }

    private static int findTicketIndex(List<Item> itemsBag) {
        if (itemsBag == null) {
            return -1;
        }
        for (int i = 0; i < itemsBag.size(); i++) {
            Item item = itemsBag.get(i);
            if (item != null && item.template != null
                    && item.template.id == ConstItem.VE_QUAY_NGOC_VANG) {
                return i;
            }
        }
        return -1;
    }

    private static boolean ticketDebitPostConditionHolds(List<Item> itemsBag, int ticketIndex,
                                                          Item ticket, int quantityBefore,
                                                          int ticketDebit) {
        if (ticketDebit == 0) {
            return true;
        }
        if (ticket == null || ticketIndex < 0 || ticketIndex >= itemsBag.size()) {
            return false;
        }

        int expectedQuantity = quantityBefore - ticketDebit;
        if (expectedQuantity < 0 || ticket.quantity != expectedQuantity) {
            return false;
        }

        Item slotItem = itemsBag.get(ticketIndex);
        if (expectedQuantity == 0) {
            return slotItem != null && !slotItem.isNotNullItem();
        }
        if (slotItem != ticket || ticket.template == null
                || ticket.template.id != ConstItem.VE_QUAY_NGOC_VANG) {
            return false;
        }

        if (ticket.itemOptions != null) {
            for (ItemOption option : ticket.itemOptions) {
                if (option != null && option.optionTemplate != null
                        && option.optionTemplate.id == 31
                        && option.param != expectedQuantity) {
                    return false;
                }
            }
        }
        return true;
    }

    private static final class RejectionRateLimiter {
        private static final int MAX_ENTRIES = 2000;
        private static final long WINDOW_MS = 5000L;
        private static final Object LOCK = new Object();
        // LinkedHashMap in insertion order
        private static final LinkedHashMap<Long, Long> rejectionLogs = new LinkedHashMap<>();

        private static boolean shouldLog(long playerId) {
            synchronized (LOCK) {
                long now = System.currentTimeMillis();
                Long last = rejectionLogs.get(playerId);
                if (last != null && (now - last) < WINDOW_MS) {
                    return false;
                }

                if (last != null) {
                    // Entry exists and has expired -> update timestamp and move to end
                    rejectionLogs.remove(playerId);
                    rejectionLogs.put(playerId, now);
                    return true;
                }

                // New player: evict expired entries from head if at or near cap
                if (rejectionLogs.size() >= MAX_ENTRIES) {
                    Iterator<Map.Entry<Long, Long>> it = rejectionLogs.entrySet().iterator();
                    while (it.hasNext()) {
                        Map.Entry<Long, Long> entry = it.next();
                        if (now - entry.getValue() >= WINDOW_MS) {
                            it.remove();
                        } else {
                            break; // Older entries are at head; once non-expired reached, stop
                        }
                    }

                    // If STILL at cap after evicting all expired entries, REFUSE new player!
                    if (rejectionLogs.size() >= MAX_ENTRIES) {
                        return false;
                    }
                }

                rejectionLogs.put(playerId, now);
                return true;
            }
        }
    }

    private static void logRejection(long playerId, int action, byte currencyType, int count, SpinStatus status) {
        if (status != SpinStatus.SUCCESS && status != SpinStatus.REOPEN_SUCCESS) {
            if (RejectionRateLimiter.shouldLog(playerId)) {
                Logger.warningln(String.format(
                        "[LuckyRound] event=REJECTION playerId=%d action=%d currency=%d count=%d status=%s",
                        playerId, action, currencyType, count, status.name()
                ));
            }
        }
    }

    public void openCrackBallUI(Player pl, byte type) {
        if (pl == null || pl.idMark == null || pl.inventory == null) {
            return;
        }
        if (type != USING_GEM && type != USING_GOLD) {
            return;
        }

        byte validatedType;
        synchronized (pl.inventory) {
            pl.idMark.setTypeLuckyRound(type);
            validatedType = pl.idMark.getTypeLuckyRound();
        }

        Message msg = null;
        try {
            msg = new Message(-127);
            msg.writer().writeByte(0);
            msg.writer().writeByte(7);
            for (int i = 0; i < 7; i++) {
                msg.writer().writeShort(419 + i);
            }
            msg.writer().writeByte(validatedType);
            msg.writer().writeInt(validatedType == USING_GEM ? PRICE_GEM : PRICE_GOLD);
            msg.writer().writeShort(ConstItem.VE_QUAY_NGOC_VANG);
            pl.sendMessage(msg);
        } catch (IOException e) {
        } finally {
            if (msg != null) {
                msg.cleanup();
            }
        }
    }

    public void openCrackBallVipUI(Player pl, byte type) {
        if (pl == null || pl.idMark == null || pl.inventory == null) {
            return;
        }
        if (type != USING_GEM && type != USING_GOLD) {
            return;
        }

        byte validatedType;
        synchronized (pl.inventory) {
            pl.idMark.setTypeLuckyRound(type);
            validatedType = pl.idMark.getTypeLuckyRound();
        }

        Message msg = null;
        try {
            msg = new Message(-127);
            msg.writer().writeByte(0);
            msg.writer().writeByte(7);
            for (int i = 0; i < 7; i++) {
                msg.writer().writeShort(419);
            }
            msg.writer().writeByte(validatedType);
            msg.writer().writeInt(validatedType == USING_GEM ? PRICE_GEM : PRICE_GOLD);
            msg.writer().writeShort(ConstItem.VE_QUAY_NGOC_VANG);
            pl.sendMessage(msg);
        } catch (IOException e) {
        } finally {
            if (msg != null) {
                msg.cleanup();
            }
        }
    }

    public void readOpenBall(Player player, Message msg) {
        processSpin(player, msg);
    }

    public SpinResult processSpin(Player player, Message msg) {
        if (player == null || msg == null || msg.reader() == null) {
            return SpinResult.failure(SpinStatus.MALFORMED_PACKET, -1, 0, (byte) -1);
        }
        if (player.idMark == null || player.inventory == null) {
            return SpinResult.failure(SpinStatus.INVENTORY_UNAVAILABLE, -1, 0, (byte) -1);
        }

        byte preflightCurrency;
        synchronized (player.inventory) {
            preflightCurrency = player.idMark.getTypeLuckyRound();
        }

        int action;
        try {
            action = msg.reader().read();
        } catch (Exception e) {
            logRejection(player.id, -1, preflightCurrency, 0, SpinStatus.MALFORMED_PACKET);
            return SpinResult.failure(SpinStatus.MALFORMED_PACKET, -1, 0, preflightCurrency);
        }
        if (action == -1) {
            logRejection(player.id, -1, preflightCurrency, 0, SpinStatus.MALFORMED_PACKET);
            return SpinResult.failure(SpinStatus.MALFORMED_PACKET, -1, 0, preflightCurrency);
        }

        if (action == 2) {
            int count;
            try {
                count = msg.reader().read();
            } catch (Exception e) {
                logRejection(player.id, action, preflightCurrency, 0, SpinStatus.MALFORMED_PACKET);
                return SpinResult.failure(SpinStatus.MALFORMED_PACKET, action, 0, preflightCurrency);
            }
            if (count == -1) {
                logRejection(player.id, action, preflightCurrency, 0, SpinStatus.MALFORMED_PACKET);
                return SpinResult.failure(SpinStatus.MALFORMED_PACKET, action, 0, preflightCurrency);
            }

            // Verify EOF (no trailing bytes)
            try {
                if (msg.reader().read() != -1) {
                    logRejection(player.id, action, preflightCurrency, count, SpinStatus.MALFORMED_PACKET);
                    return SpinResult.failure(SpinStatus.MALFORMED_PACKET, action, count, preflightCurrency);
                }
            } catch (Exception e) {
                logRejection(player.id, action, preflightCurrency, count, SpinStatus.MALFORMED_PACKET);
                return SpinResult.failure(SpinStatus.MALFORMED_PACKET, action, count, preflightCurrency);
            }

            if (count < MIN_ALLOWED_COUNT || count > MAX_ALLOWED_COUNT) {
                logRejection(player.id, action, preflightCurrency, count, SpinStatus.INVALID_COUNT);
                return SpinResult.failure(SpinStatus.INVALID_COUNT, action, count, preflightCurrency);
            }

            SpinResult result = executeSpin(player, count);
            if (result.isSuccess()) {
                sendReward(player, result.rewards);
                if (result.paidCount > 0) {
                    Service.gI().sendMoney(player);
                }
                if (result.ticketDebited > 0) {
                    InventoryService.gI().sendItemBags(player);
                }
            } else {
                logRejection(player.id, action, result.currencyType, count, result.status);
                sendFailureNotification(player, result.status, result.currencyType);
            }
            return result;
        }

        // Reopen: client sends typePrice byte only (0 for gold, 7 for gem)
        if (action == USING_GOLD || action == USING_GEM) {
            try {
                if (msg.reader().read() != -1) {
                    logRejection(player.id, action, preflightCurrency, 0, SpinStatus.INVALID_ACTION);
                    return SpinResult.failure(SpinStatus.INVALID_ACTION, action, 0, preflightCurrency);
                }
            } catch (Exception e) {
                logRejection(player.id, action, preflightCurrency, 0, SpinStatus.INVALID_ACTION);
                return SpinResult.failure(SpinStatus.INVALID_ACTION, action, 0, preflightCurrency);
            }

            byte currentCurrency;
            synchronized (player.inventory) {
                currentCurrency = player.idMark.getTypeLuckyRound();
            }

            if (action == currentCurrency) {
                if (action == USING_GEM) {
                    openCrackBallVipUI(player, (byte) action);
                } else {
                    openCrackBallUI(player, (byte) action);
                }
                return new SpinResult(SpinStatus.REOPEN_SUCCESS, action, 0, (byte) action, 0L, 0L, 0L, 0L, 0, 0, 0, null, 0, 0);
            } else {
                logRejection(player.id, action, currentCurrency, 0, SpinStatus.INVALID_ACTION);
                return SpinResult.failure(SpinStatus.INVALID_ACTION, action, 0, currentCurrency);
            }
        }

        logRejection(player.id, action, preflightCurrency, 0, SpinStatus.INVALID_ACTION);
        return SpinResult.failure(SpinStatus.INVALID_ACTION, action, 0, preflightCurrency);
    }

    public SpinResult executeSpin(Player player, int count) {
        if (player == null || player.idMark == null || player.inventory == null) {
            return SpinResult.failure(SpinStatus.INVENTORY_UNAVAILABLE, 2, count, (byte) -1);
        }

        byte type;
        long requestedCost;
        long unitPrice;
        int ticketDebit;
        int paidCount;
        Item preflightTicket = null;
        int preflightTicketIndex = -1;
        int preflightTicketQuantity = 0;

        // ==========================================
        // PHASE 1: PREFLIGHT UNDER INVENTORY MONITOR
        // ==========================================
        synchronized (player.inventory) {
            if (!player.inventory.isActive() || player.inventory.itemsBoxCrackBall == null || player.inventory.itemsBag == null) {
                return SpinResult.failure(SpinStatus.INVENTORY_UNAVAILABLE, 2, count, (byte) -1);
            }

            type = player.idMark.getTypeLuckyRound();
            if (type != USING_GEM && type != USING_GOLD) {
                return SpinResult.failure(SpinStatus.UNSUPPORTED_CURRENCY, 2, count, type);
            }

            if (count < MIN_ALLOWED_COUNT || count > MAX_ALLOWED_COUNT) {
                return SpinResult.failure(SpinStatus.INVALID_COUNT, 2, count, type);
            }

            // Find tickets for mixed payment offset
            preflightTicketIndex = findTicketIndex(player.inventory.itemsBag);
            if (preflightTicketIndex >= 0) {
                preflightTicket = player.inventory.itemsBag.get(preflightTicketIndex);
                if (preflightTicket.quantity <= 0) {
                    return SpinResult.failure(SpinStatus.INVALID_TICKET, 2, count, type);
                }
                preflightTicketQuantity = preflightTicket.quantity;
            }
            ticketDebit = Math.min(preflightTicketQuantity, count);
            paidCount = count - ticketDebit;

            // Calculate currency cost for paid spins only
            if (paidCount > 0) {
                unitPrice = (type == USING_GEM) ? PRICE_GEM : PRICE_GOLD;
                try {
                    requestedCost = Math.multiplyExact((long) paidCount, unitPrice);
                } catch (ArithmeticException e) {
                    return SpinResult.failure(SpinStatus.INVALID_COUNT, 2, count, type);
                }
                if (requestedCost < 0) {
                    return SpinResult.failure(SpinStatus.INVALID_COUNT, 2, count, type);
                }
            } else {
                unitPrice = 0;
                requestedCost = 0;
            }

            int currentBoxSize = player.inventory.itemsBoxCrackBall.size();
            if (currentBoxSize < 0 || currentBoxSize > MAX_ITEM_IN_BOX) {
                return SpinResult.failure(SpinStatus.BOX_FULL, 2, count, type);
            }
            if (count > MAX_ITEM_IN_BOX - currentBoxSize) {
                return SpinResult.failure(SpinStatus.BOX_FULL, 2, count, type);
            }

            // Validate currency balance only for paidCount > 0
            if (paidCount > 0) {
                if (type == USING_GEM) {
                    if (player.inventory.gem < 0 || player.inventory.gem > PlayerConfig.getMaxGem()) {
                        return SpinResult.failure(SpinStatus.INVALID_BALANCE, 2, count, type);
                    }
                    if (player.inventory.gem < requestedCost) {
                        return SpinResult.failure(SpinStatus.INSUFFICIENT_FUNDS, 2, count, type);
                    }
                } else {
                    if (player.inventory.gold < 0 || player.inventory.gold > PlayerConfig.getMaxGold()) {
                        return SpinResult.failure(SpinStatus.INVALID_BALANCE, 2, count, type);
                    }
                    if (player.inventory.gold < requestedCost) {
                        return SpinResult.failure(SpinStatus.INSUFFICIENT_FUNDS, 2, count, type);
                    }
                }
            }
        }

        // ==========================================
        // PHASE 2: GENERATION OUTSIDE INVENTORY MONITOR
        // ==========================================
        boolean isVip = (type == USING_GEM);
        List<Item> candidateRewards;
        try {
            candidateRewards = RewardService.gI().getListItemLuckyRound(player, count, isVip);
        } catch (RuntimeException e) {
            return SpinResult.failure(SpinStatus.REWARD_GENERATION_FAILED, 2, count, type);
        }
        if (candidateRewards == null || candidateRewards.size() != count) {
            return SpinResult.failure(SpinStatus.REWARD_GENERATION_FAILED, 2, count, type);
        }
        for (Item item : candidateRewards) {
            if (item == null || item.template == null) {
                return SpinResult.failure(SpinStatus.REWARD_GENERATION_FAILED, 2, count, type);
            }
        }

        // ==========================================
        // PHASE 3: ATOMIC COMMIT & ROLLBACK
        // ==========================================
        synchronized (player.inventory) {
            // Re-validate state
            if (!player.inventory.isActive() || player.inventory.itemsBoxCrackBall == null || player.inventory.itemsBag == null) {
                return SpinResult.failure(SpinStatus.INVENTORY_UNAVAILABLE, 2, count, type);
            }
            if (player.idMark.getTypeLuckyRound() != type) {
                return SpinResult.failure(SpinStatus.UNSUPPORTED_CURRENCY, 2, count, type);
            }

            int boxSizeBefore = player.inventory.itemsBoxCrackBall.size();
            if (boxSizeBefore < 0 || boxSizeBefore > MAX_ITEM_IN_BOX || count > MAX_ITEM_IN_BOX - boxSizeBefore) {
                return SpinResult.failure(SpinStatus.BOX_FULL, 2, count, type);
            }

            // The payment plan authorized in Phase 1 is immutable. A ticket
            // snapshot change requires a retry; it must never reprice the spin.
            int ticketIndex = preflightTicketIndex;
            Item liveTicket = preflightTicket;
            ItemSnapshot ticketSnapshot = null;

            if (preflightTicket != null) {
                if (ticketIndex < 0 || ticketIndex >= player.inventory.itemsBag.size()
                        || player.inventory.itemsBag.get(ticketIndex) != preflightTicket
                        || preflightTicket.template == null
                        || preflightTicket.template.id != ConstItem.VE_QUAY_NGOC_VANG
                        || preflightTicket.quantity != preflightTicketQuantity) {
                    return SpinResult.failure(SpinStatus.STATE_CHANGED, 2, count, type);
                }
            } else if (findTicketIndex(player.inventory.itemsBag) >= 0) {
                return SpinResult.failure(SpinStatus.STATE_CHANGED, 2, count, type);
            }

            long balanceBefore;
            long balanceAfter;

            if (type == USING_GEM) {
                int gemBefore = player.inventory.gem;
                if (paidCount > 0) {
                    int maxGem = PlayerConfig.getMaxGem();
                    if (gemBefore < 0 || gemBefore > maxGem) {
                        return SpinResult.failure(SpinStatus.INVALID_BALANCE, 2, count, type);
                    }
                    if ((long) gemBefore < requestedCost) {
                        return SpinResult.failure(SpinStatus.INSUFFICIENT_FUNDS, 2, count, type);
                    }
                }
                balanceBefore = gemBefore;

                try {
                    // Deep snapshot ticket before any mutation
                    if (ticketDebit > 0 && liveTicket != null) {
                        ticketSnapshot = new ItemSnapshot(liveTicket);
                    }

                    // Debit tickets first (non-destructive: direct quantity decrement)
                    if (ticketDebit > 0 && liveTicket != null) {
                        liveTicket.quantity -= ticketDebit;
                        if (liveTicket.quantity <= 0) {
                            if (ticketIndex >= 0 && ticketIndex < player.inventory.itemsBag.size()) {
                                player.inventory.itemsBag.set(ticketIndex, ItemService.gI().createItemNull());
                            }
                        } else {
                            syncQuantityOption(liveTicket);
                        }
                    }

                    // Debit currency
                    if (paidCount > 0) {
                        player.inventory.gem -= (int) requestedCost;
                    }
                    player.inventory.itemsBoxCrackBall.addAll(candidateRewards);

                    balanceAfter = player.inventory.gem;
                    int boxSizeAfter = player.inventory.itemsBoxCrackBall.size();

                    // Post-conditions
                    if (balanceBefore - balanceAfter != requestedCost
                            || boxSizeAfter - boxSizeBefore != count
                            || !ticketDebitPostConditionHolds(player.inventory.itemsBag, ticketIndex,
                                    liveTicket, preflightTicketQuantity, ticketDebit)) {
                        throw new IllegalStateException("Post-condition check failed");
                    }

                    // Post-commit disposal of exhausted ticket
                    if (ticketDebit > 0 && liveTicket != null && liveTicket.quantity <= 0) {
                        liveTicket.dispose();
                    }

                    return new SpinResult(
                            SpinStatus.SUCCESS, 2, count, type,
                            requestedCost, requestedCost,
                            balanceBefore, balanceAfter,
                            boxSizeBefore, boxSizeAfter,
                            candidateRewards.size(), candidateRewards,
                            ticketDebit, paidCount
                    );
                } catch (RuntimeException e) {
                    try {
                        // Rollback Gem
                        player.inventory.gem = gemBefore;
                        // Rollback ticket with full metadata restore
                        if (ticketDebit > 0 && ticketSnapshot != null && liveTicket != null) {
                            ticketSnapshot.restore(liveTicket);
                            if (ticketIndex >= 0 && ticketIndex < player.inventory.itemsBag.size()) {
                                player.inventory.itemsBag.set(ticketIndex, liveTicket);
                            }
                        }
                        // Truncate box by precisely removing the candidate rewards that were appended
                        for (int i = candidateRewards.size() - 1; i >= 0; i--) {
                            int targetIndex = boxSizeBefore + i;
                            if (targetIndex < player.inventory.itemsBoxCrackBall.size()) {
                                if (player.inventory.itemsBoxCrackBall.get(targetIndex) == candidateRewards.get(i)) {
                                    player.inventory.itemsBoxCrackBall.remove(targetIndex);
                                }
                            }
                        }
                    } catch (Exception rollbackEx) {
                        Logger.warningln(String.format(
                                "[LuckyRound] event=ROLLBACK_CRITICAL_FAILURE playerId=%d cause=%s rollbackCause=%s",
                                player.id, e.getMessage(), rollbackEx.getMessage()
                        ));
                        throw rollbackEx;
                    }
                    return SpinResult.failure(SpinStatus.COMMIT_FAILED, 2, count, type);
                }

            } else {
                // USING_GOLD
                long goldBefore = player.inventory.gold;
                if (paidCount > 0) {
                    long maxGold = PlayerConfig.getMaxGold();
                    if (goldBefore < 0 || goldBefore > maxGold) {
                        return SpinResult.failure(SpinStatus.INVALID_BALANCE, 2, count, type);
                    }
                    if (goldBefore < requestedCost) {
                        return SpinResult.failure(SpinStatus.INSUFFICIENT_FUNDS, 2, count, type);
                    }
                }
                balanceBefore = goldBefore;

                try {
                    // Deep snapshot ticket before any mutation
                    if (ticketDebit > 0 && liveTicket != null) {
                        ticketSnapshot = new ItemSnapshot(liveTicket);
                    }

                    // Debit tickets first (non-destructive: direct quantity decrement)
                    if (ticketDebit > 0 && liveTicket != null) {
                        liveTicket.quantity -= ticketDebit;
                        if (liveTicket.quantity <= 0) {
                            if (ticketIndex >= 0 && ticketIndex < player.inventory.itemsBag.size()) {
                                player.inventory.itemsBag.set(ticketIndex, ItemService.gI().createItemNull());
                            }
                        } else {
                            syncQuantityOption(liveTicket);
                        }
                    }

                    // Debit currency
                    if (paidCount > 0) {
                        player.inventory.gold -= requestedCost;
                    }
                    player.inventory.itemsBoxCrackBall.addAll(candidateRewards);

                    balanceAfter = player.inventory.gold;
                    int boxSizeAfter = player.inventory.itemsBoxCrackBall.size();

                    // Post-conditions
                    if (balanceBefore - balanceAfter != requestedCost
                            || boxSizeAfter - boxSizeBefore != count
                            || !ticketDebitPostConditionHolds(player.inventory.itemsBag, ticketIndex,
                                    liveTicket, preflightTicketQuantity, ticketDebit)) {
                        throw new IllegalStateException("Post-condition check failed");
                    }

                    // Post-commit disposal of exhausted ticket
                    if (ticketDebit > 0 && liveTicket != null && liveTicket.quantity <= 0) {
                        liveTicket.dispose();
                    }

                    return new SpinResult(
                            SpinStatus.SUCCESS, 2, count, type,
                            requestedCost, requestedCost,
                            balanceBefore, balanceAfter,
                            boxSizeBefore, boxSizeAfter,
                            candidateRewards.size(), candidateRewards,
                            ticketDebit, paidCount
                    );
                } catch (RuntimeException e) {
                    try {
                        // Rollback Gold
                        player.inventory.gold = goldBefore;
                        // Rollback ticket with full metadata restore
                        if (ticketDebit > 0 && ticketSnapshot != null && liveTicket != null) {
                            ticketSnapshot.restore(liveTicket);
                            if (ticketIndex >= 0 && ticketIndex < player.inventory.itemsBag.size()) {
                                player.inventory.itemsBag.set(ticketIndex, liveTicket);
                            }
                        }
                        // Truncate box by precisely removing the candidate rewards that were appended
                        for (int i = candidateRewards.size() - 1; i >= 0; i--) {
                            int targetIndex = boxSizeBefore + i;
                            if (targetIndex < player.inventory.itemsBoxCrackBall.size()) {
                                if (player.inventory.itemsBoxCrackBall.get(targetIndex) == candidateRewards.get(i)) {
                                    player.inventory.itemsBoxCrackBall.remove(targetIndex);
                                }
                            }
                        }
                    } catch (Exception rollbackEx) {
                        Logger.warningln(String.format(
                                "[LuckyRound] event=ROLLBACK_CRITICAL_FAILURE playerId=%d cause=%s rollbackCause=%s",
                                player.id, e.getMessage(), rollbackEx.getMessage()
                        ));
                        throw rollbackEx;
                    }
                    return SpinResult.failure(SpinStatus.COMMIT_FAILED, 2, count, type);
                }
            }
        }
    }

    private void sendFailureNotification(Player player, SpinStatus status, byte currency) {
        if (player == null) {
            return;
        }
        switch (status) {
            case INSUFFICIENT_FUNDS:
            case INVALID_BALANCE:
                if (currency == USING_GEM) {
                    Service.gI().sendThongBao(player, "Bạn không đủ ngọc để mở");
                } else if (currency == USING_GOLD) {
                    Service.gI().sendThongBao(player, "Bạn không đủ vàng để mở");
                }
                break;
            case BOX_FULL:
                Service.gI().sendThongBao(player, "Rương phụ đã đầy");
                break;
            case INVALID_TICKET:
                Service.gI().sendThongBao(player, "Bạn không đủ vé để quay");
                break;
            case COMMIT_FAILED:
                Service.gI().sendThongBao(player, "Giao dịch thất bại, vui lòng thử lại");
                break;
            case STATE_CHANGED:
                Service.gI().sendThongBao(player, "Hành trang đã thay đổi, vui lòng thử lại");
                break;
            default:
                break;
        }
    }

    private void sendReward(Player player, List<Item> items) {
        if (player == null || items == null) {
            return;
        }
        Message msg = null;
        try {
            msg = new Message(-127);
            msg.writer().writeByte(1);
            msg.writer().writeByte(items.size());
            for (Item item : items) {
                msg.writer().writeShort(item != null && item.template != null ? item.template.iconID : -1);
            }
            player.sendMessage(msg);
        } catch (IOException e) {
        } finally {
            if (msg != null) {
                msg.cleanup();
            }
        }
    }

}
