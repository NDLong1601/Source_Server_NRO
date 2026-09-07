package nro.models.services_func;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.UUID;
import nro.models.Bot.Bot;
import nro.models.database.HistoryTransactionDAO;
import nro.models.item.Item;
import nro.models.network.Message;
import nro.models.player.Player;
import nro.models.player.PlayerConfig;
import nro.models.server.ServerManager;
import nro.models.services.CostumeCollectionService;
import nro.models.services.InventoryService;
import nro.models.services.ItemService;
import nro.models.services.PlayerService;
import nro.models.services.Service;
import nro.models.utils.Logger;
import nro.models.utils.Util;

public class Trade {

    public static final int TIME_TRADE = 180000;
    public static final int QUANLITY_MAX = 2_000_000_000;
    public static final int MAX_GOLD_TRADE_PER_TIME = 10_000_000;
    public static final int MAX_TRADE_ITEMS = 10;

    public static final byte SUCCESS = 0;
    public static final byte FAIL_MAX_GOLD_PLAYER1 = 1;
    public static final byte FAIL_MAX_GOLD_PLAYER2 = 2;
    public static final byte FAIL_NOT_ENOUGH_BAG_P1 = 3;
    public static final byte FAIL_NOT_ENOUGH_BAG_P2 = 4;
    public static final byte FAIL_ACTIVE = 5;
    public static final byte FAIL_TRADE_LIMIT = 6;
    public static final byte FAIL_INVALID_OFFER = 7;

    private final String tradeId = UUID.randomUUID().toString();
    private final Player player1;
    private final Player player2;
    private final TradeStateMachine stateMachine;
    private final TradeLedgerSink ledgerSink;
    private final Object tradeLock = new Object();

    private final List<Item> itemsTrade1 = new ArrayList<>();
    private final List<Item> itemsTrade2 = new ArrayList<>();
    private final IdentityHashMap<Item, SourceItemRef> sourcesTrade1 = new IdentityHashMap<>();
    private final IdentityHashMap<Item, SourceItemRef> sourcesTrade2 = new IdentityHashMap<>();
    private volatile int goldTrade1;
    private volatile int goldTrade2;
    private boolean botOfferPrepared;

    public volatile byte accept;

    private long lastTimeStart;
    private volatile boolean start;

    public Trade(Player pl1, Player pl2) {
        this(pl1, pl2, HistoryTransactionDAO.ledgerSink());
    }

    Trade(Player pl1, Player pl2, TradeLedgerSink ledgerSink) {
        if (pl1 == null || pl2 == null) {
            throw new IllegalArgumentException("Players must not be null");
        }
        if (pl1.id == pl2.id) {
            throw new IllegalArgumentException("Self-trade is not allowed");
        }
        this.player1 = pl1;
        this.player2 = pl2;
        this.ledgerSink = ledgerSink;
        this.stateMachine = new TradeStateMachine(pl1.id, pl2.id);
        this.lastTimeStart = System.currentTimeMillis();
        if (ledgerSink == null) {
            throw new IllegalArgumentException("Trade ledger sink must not be null");
        }
        if (!TradeRegistry.gI().register(this)) {
            throw new IllegalStateException("A participant already has an active trade");
        }
    }

    public String getTradeId() {
        return tradeId;
    }

    public Player getPlayer1() {
        return player1;
    }

    public Player getPlayer2() {
        return player2;
    }

    public TradeStateMachine getStateMachine() {
        return stateMachine;
    }

    public List<Item> getItemsTrade1() {
        synchronized (tradeLock) {
            return Collections.unmodifiableList(InventoryService.gI().copyList(itemsTrade1));
        }
    }

    public List<Item> getItemsTrade2() {
        synchronized (tradeLock) {
            return Collections.unmodifiableList(InventoryService.gI().copyList(itemsTrade2));
        }
    }

    List<Item> getOfferItems1Snapshot() {
        synchronized (tradeLock) {
            return new ArrayList<>(itemsTrade1);
        }
    }

    List<Item> getOfferItems2Snapshot() {
        synchronized (tradeLock) {
            return new ArrayList<>(itemsTrade2);
        }
    }

    SourceItemRef getSourceRef(Player owner, Item offer) {
        synchronized (tradeLock) {
            if (owner == player1) {
                return sourcesTrade1.get(offer);
            }
            if (owner == player2) {
                return sourcesTrade2.get(offer);
            }
            return null;
        }
    }

    static final class SourceItemRef {
        final Item source;
        final int originalIndex;
        final Item identitySnapshot;

        SourceItemRef(Item source, int originalIndex) {
            this.source = source;
            this.originalIndex = originalIndex;
            this.identitySnapshot = ItemService.gI().copyItem(source);
        }

        boolean matches(Item live) {
            if (live != source || live == null || !live.isNotNullItem()
                    || identitySnapshot == null || !identitySnapshot.isNotNullItem()
                    || live.template.id != identitySnapshot.template.id
                    || live.createTime != identitySnapshot.createTime) {
                return false;
            }
            List<Item.ItemOption> left = live.itemOptions;
            List<Item.ItemOption> right = identitySnapshot.itemOptions;
            if (left == null || right == null || left.size() != right.size()) {
                return false;
            }
            for (int i = 0; i < left.size(); i++) {
                Item.ItemOption a = left.get(i);
                Item.ItemOption b = right.get(i);
                if (a == null || b == null || a.optionTemplate == null || b.optionTemplate == null
                        || a.optionTemplate.id != b.optionTemplate.id || a.param != b.param) {
                    return false;
                }
            }
            return true;
        }
    }

    public int getGoldTrade1() {
        return goldTrade1;
    }

    public int getGoldTrade2() {
        return goldTrade2;
    }

    public void openTabTrade() {
        if (!stateMachine.open()) {
            return;
        }
        if (player1.idMark != null) {
            player1.idMark.setAcpTrade(true);
        }
        if (player2.idMark != null) {
            player2.idMark.setAcpTrade(true);
        }
        this.lastTimeStart = System.currentTimeMillis();
        this.start = true;

        sendOpenTabPacket(player1, player2);
        sendOpenTabPacket(player2, player1);
    }

    private void sendOpenTabPacket(Player recipient, Player opponent) {
        if (recipient == null || recipient.getSession() == null) {
            return;
        }
        Message msg = null;
        try {
            msg = new Message(-86);
            msg.writer().writeByte(1);
            msg.writer().writeInt((int) opponent.id);
            recipient.sendMessage(msg);
        } catch (Exception ignored) {
        } finally {
            if (msg != null) {
                msg.cleanup();
            }
        }
    }

    public void addItemTrade(Player pl, byte index, int quantity) {
        String cancelReason = null;
        boolean removeRejectedItem = false;
        synchronized (tradeLock) {
            if (pl == null) {
                return;
            }
            if (pl != player1 && pl != player2) {
                Service.gI().sendThongBao(pl, "Bạn không tham gia giao dịch này");
                return;
            }
            if (!stateMachine.canModifyOffer(pl)) {
                Service.gI().sendThongBao(pl, "Giao dịch đã bị khóa hoặc kết thúc, không thể thay đổi vật phẩm");
                return;
            }
            if (pl.getSession() != null && !pl.getSession().actived) {
                Service.gI().sendThongBaoFromAdmin(pl,
                        "|5|VUI LÒNG KÍCH HOẠT TÀI KHOẢN TẠI\n|7|Liên Hệ Admin\n|5 ĐỂ MỞ GIAO DỊCH");
                removeRejectedItem = index != -1;
            } else if (index == -1) {
                if (pl.inventory == null || quantity < 0 || quantity > MAX_GOLD_TRADE_PER_TIME
                        || quantity > pl.inventory.gold) {
                    cancelReason = "Số vàng giao dịch không hợp lệ hoặc vượt quá số dư";
                } else if (pl == player1) {
                    goldTrade1 = quantity;
                } else {
                    goldTrade2 = quantity;
                }
            } else if (pl.inventory == null || pl.inventory.itemsBag == null
                    || index < 0 || index >= pl.inventory.itemsBag.size()) {
                cancelReason = "Vị trí vật phẩm giao dịch không hợp lệ";
            } else {
                Item item = pl.inventory.itemsBag.get(index);
                if (quantity == 0) {
                    quantity = 1;
                }
                if (item == null || !item.isNotNullItem() || quantity < 0
                        || quantity > QUANLITY_MAX || quantity > item.quantity) {
                    cancelReason = "Số lượng vật phẩm giao dịch không hợp lệ";
                } else if (isItemCannotTran(item)) {
                    removeRejectedItem = true;
                } else {
                    List<Item> targetItems = pl == player1 ? itemsTrade1 : itemsTrade2;
                    IdentityHashMap<Item, SourceItemRef> targetSources =
                            pl == player1 ? sourcesTrade1 : sourcesTrade2;
                    boolean duplicateSource = targetSources.values().stream()
                            .anyMatch(ref -> ref.source == item);
                    long slotsNeededLong = ((long) quantity + 98L) / 99L;
                    int slotsNeeded = slotsNeededLong > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) slotsNeededLong;
                    if (duplicateSource) {
                        cancelReason = "Một vật phẩm không thể được thêm vào giao dịch nhiều lần";
                    } else if (slotsNeeded <= 0 || targetItems.size() + slotsNeeded > MAX_TRADE_ITEMS) {
                        Service.gI().sendThongBao(pl, "Số ô giao dịch vượt quá giới hạn " + MAX_TRADE_ITEMS);
                        return;
                    } else {
                        SourceItemRef sourceRef = new SourceItemRef(item, index);
                        int remaining = quantity;
                        while (remaining > 0) {
                            int part = Math.min(99, remaining);
                            Item offered = ItemService.gI().copyItem(item);
                            offered.id = index;
                            offered.quantity = part;
                            offered.quantityGD = part;
                            targetItems.add(offered);
                            targetSources.put(offered, sourceRef);
                            remaining -= part;
                        }
                    }
                }
            }
        }
        if (removeRejectedItem) {
            removeItemTrade(pl, index);
        }
        if (cancelReason != null) {
            Service.gI().sendThongBao(pl, cancelReason);
            cancelTrade(pl);
        }
    }

    public void removeOfferItem(Player pl, byte index) {
        synchronized (tradeLock) {
            if (pl == null || (pl != player1 && pl != player2)
                    || !stateMachine.canModifyOffer(pl)) {
                return;
            }
            List<Item> targetItems = pl == player1 ? itemsTrade1 : itemsTrade2;
            IdentityHashMap<Item, SourceItemRef> targetSources =
                    pl == player1 ? sourcesTrade1 : sourcesTrade2;
            List<Item> removed = new ArrayList<>();
            for (Item offered : targetItems) {
                SourceItemRef ref = targetSources.get(offered);
                if (ref != null && ref.originalIndex == index) {
                    removed.add(offered);
                }
            }
            targetItems.removeAll(removed);
            for (Item offered : removed) {
                targetSources.remove(offered);
            }
        }
    }

    public void sendRemoveOfferItemPacket(Player pl, byte index) {
        if (pl == null || pl.getSession() == null) return;
        Message msg = null;
        try {
            msg = new Message(-86);
            msg.writer().writeByte(2);
            msg.writer().writeByte(index);
            pl.sendMessage(msg);
            Service.gI().sendThongBao(pl, "Không thể giao dịch vật phẩm này");
        } catch (Exception ignored) {
        } finally {
            if (msg != null) {
                msg.cleanup();
            }
        }
    }

    private void removeItemTrade(Player pl, byte index) {
        sendRemoveOfferItemPacket(pl, index);
    }

    public static boolean isItemCannotTran(Item item) {
        if (item == null || item.template == null) {
            return true;
        }
        Item.ItemOption tradeLimit = item.getOptionById(232);
        if (tradeLimit != null && tradeLimit.param <= 0) {
            return true;
        }
        if (item.itemOptions != null) {
            for (Item.ItemOption io : item.itemOptions) {
                if (io != null && io.optionTemplate != null && io.optionTemplate.id == 30) {
                    return true;
                }
            }
        }
        switch (item.template.id) {
            case 454:
            case 921:
            case 570: // Rương Gỗ
                return true;
        }
        switch (item.template.type) {
            case 27:
                return item.template.id == 590;
            case 5: // cải trang
            case 6: // đậu thần
            case 7: // sách skill
            case 8: // vật phẩm nhiệm vụ
            case 11: // flag bag
            case 13: // bùa
            case 22: // vệ tinh
            case 23: // ván bay
            case 24: // ván bay vip
            case 28: // cờ
            case 31: // bánh trung thu, bánh tết
            case 32: // giáp tập luyện
                return true;
            default:
                return false;
        }
    }

    public void cancelTrade() {
        cancelFromSystem("CANCELLED_BY_SYSTEM");
    }

    public void cancelTrade(Player actor) {
        if (actor != player1 && actor != player2) {
            if (actor != null) {
                Service.gI().sendThongBao(actor, "Bạn không tham gia giao dịch này");
            }
            return;
        }
        cancelInternal("CANCELLED_BY_PLAYER", "Giao dịch bị hủy bỏ");
    }

    void cancelFromSystem(String resultCode) {
        cancelInternal(resultCode, "Giao dịch bị hủy bỏ");
    }

    private void cancelInternal(String resultCode, String notification) {
        if (!stateMachine.cancel()) {
            return;
        }
        persistTerminal("CANCELLED", resultCode);
        Service.gI().sendThongBao(player1, notification);
        Service.gI().sendThongBao(player2, notification);
        closeTab();
        dispose();
    }

    private void persistTerminal(String status, String resultCode) {
        try {
            ledgerSink.terminal(TradeCommitPlan.snapshot(this), status, resultCode);
        } catch (Throwable t) {
            Logger.logException(Trade.class, new Exception("Trade terminal ledger failed tradeId=" + tradeId, t));
        }
    }

    private void closeTab() {
        Message msg = null;
        try {
            msg = new Message(-86);
            msg.writer().writeByte(7);
            if (player1 != null && player1.getSession() != null) {
                player1.sendMessage(msg);
            }
            if (player2 != null && player2.getSession() != null) {
                player2.sendMessage(msg);
            }
        } catch (Exception ignored) {
        } finally {
            if (msg != null) {
                msg.cleanup();
            }
        }
    }

    public void dispose() {
        if (player1 != null && player1.idMark != null) {
            player1.idMark.setPlayerTradeId(-1);
        }
        if (player2 != null && player2.idMark != null) {
            player2.idMark.setPlayerTradeId(-1);
        }
        TradeRegistry.gI().unregister(this);
    }

    public void lockTran(Player pl) {
        if (pl != player1 && pl != player2) {
            if (pl != null) {
                Service.gI().sendThongBao(pl, "Bạn không tham gia giao dịch này");
            }
            return;
        }
        Player recipient;
        int goldToSend;
        List<Item> itemsToSend;
        TradeStateMachine.LockStatus status;
        synchronized (tradeLock) {
            status = stateMachine.lock(pl);
            if (status != TradeStateMachine.LockStatus.SUCCESS) {
                if (status == TradeStateMachine.LockStatus.UNAUTHORIZED) {
                    Service.gI().sendThongBao(pl, "Bạn không tham gia giao dịch này");
                }
                return;
            }
            recipient = pl == player1 ? player2 : player1;
            goldToSend = pl == player1 ? goldTrade1 : goldTrade2;
            itemsToSend = new ArrayList<>(pl == player1 ? itemsTrade1 : itemsTrade2);
        }

        if (!sendLockOfferPacket(recipient, goldToSend, itemsToSend)) {
            cancelFromSystem("WIRE_PAYLOAD_INVALID");
            return;
        }

        if (player2 != null && player2.isBot && pl == player1) {
            if (player2 instanceof Bot && ((Bot) player2).shop != null) {
                ((Bot) player2).shop.CheckTraDe(itemsToSend);
            }
        }
    }

    private boolean sendLockOfferPacket(Player recipient, int gold, List<Item> items) {
        if (recipient == null || recipient.getSession() == null) {
            return true;
        }
        Message msg = null;
        try {
            msg = new Message(-86);
            msg.writer().writeByte(6);
            msg.writer().writeInt(gold);

            List<Item> validItems = new ArrayList<>();
            if (items != null) {
                for (Item item : items) {
                    if (item != null && item.template != null) {
                        if (recipient.getSession().version < 222 && item.quantity > 99) {
                            int remaining = item.quantity;
                            while (remaining > 0) {
                                Item part = ItemService.gI().copyItem(item);
                                part.quantity = Math.min(99, remaining);
                                validItems.add(part);
                                remaining -= part.quantity;
                            }
                        } else {
                            validItems.add(item);
                        }
                    }
                }
            }
            if (validItems.size() > 255) {
                return false;
            }
            msg.writer().writeByte(validItems.size());
            int version = recipient.getSession().version;
            for (Item item : validItems) {
                msg.writer().writeShort(item.template.id);
                if (version < 222) {
                    if (item.quantity < 0 || item.quantity > 99) {
                        return false;
                    }
                    msg.writer().writeByte(item.quantity);
                } else {
                    msg.writer().writeInt(item.quantity);
                }
                List<Item.ItemOption> itemOptions = ItemService.gI().getVisibleItemOptions(item);
                List<Item.ItemOption> validOptions = new ArrayList<>();
                if (itemOptions != null) {
                    for (Item.ItemOption io : itemOptions) {
                        if (io != null && io.optionTemplate != null) {
                            validOptions.add(io);
                        }
                    }
                }
                if (validOptions.size() > 255) {
                    return false;
                }
                msg.writer().writeByte(validOptions.size());
                for (Item.ItemOption io : validOptions) {
                    msg.writer().writeByte((byte) io.optionTemplate.id);
                    msg.writer().writeShort((short) io.param);
                }
            }
            if (msg.getData().length > 65535) {
                return false;
            }
            recipient.sendMessage(msg);
            return true;
        } catch (Exception e) {
            Logger.logException(Trade.class, e);
            return false;
        } finally {
            if (msg != null) {
                msg.cleanup();
            }
        }
    }

    /**
     * Legacy acceptTrade() adapter: defaults to player1 if called without actor.
     */
    public void acceptTrade() {
        acceptTrade(player1);
    }

    public void acceptTrade(Player actor) {
        if (actor != player1 && actor != player2) {
            if (actor != null) {
                Service.gI().sendThongBao(actor, "Bạn không tham gia giao dịch này");
            }
            return;
        }
        TradeStateMachine.AcceptStatus status = stateMachine.accept(actor);
        switch (status) {
            case UNAUTHORIZED:
                Service.gI().sendThongBao(actor, "Bạn không tham gia giao dịch này");
                break;
            case NOT_LOCKED:
                Service.gI().sendThongBao(actor, "Cả hai bên phải khóa giao dịch trước khi đồng ý");
                break;
            case WAITING_OPPONENT:
                this.accept = 1;
                Service.gI().sendThongBao(actor, "Xin chờ đối phương đồng ý");
                break;
            case ALREADY_ACCEPTED:
                Service.gI().sendThongBao(actor, "Bạn đã đồng ý rồi, xin chờ đối phương");
                break;
            case READY_TO_COMMIT:
                this.accept = 2;
                startTrade();
                break;
            case TERMINAL:
                break;
        }
    }

    private void startTrade() {
        TradeCommitPlan.ImmutableTradeData intent = null;
        try {
            intent = TradeCommitPlan.snapshot(this);
            if (!ledgerSink.begin(intent)) {
                stateMachine.failCommit();
                sendNotifyTrade(FAIL_INVALID_OFFER);
                closeTab();
                dispose();
                return;
            }
            TradeCommitPlan.CommitResult result = TradeCommitPlan.execute(this);
            if (result.success) {
                if (!stateMachine.markCommitted()) {
                    throw new IllegalStateException("Commit owner lost COMMITTING state for tradeId=" + tradeId);
                }
                recordOwnershipAfterCommit(result.tradeData);

                if (player1.idMark != null) player1.idMark.setLastTimeTrade(System.currentTimeMillis());
                if (player2.idMark != null) player2.idMark.setLastTimeTrade(System.currentTimeMillis());

                InventoryService.gI().sendItemBags(player1);
                if (!player2.isBot) {
                    InventoryService.gI().sendItemBags(player2);
                }
                PlayerService.gI().sendInfoHpMpMoney(player1);
                if (!player2.isBot) {
                    PlayerService.gI().sendInfoHpMpMoney(player2);
                }

                if (!ledgerSink.committed(result.tradeData)) {
                    Logger.error("[TradeLedger] Trade committed but ledger finalization needs reconciliation tradeId=" + tradeId);
                }
                sendNotifyTrade(SUCCESS);
                closeTab();
                dispose();
            } else {
                stateMachine.failCommit();
                ledgerSink.terminal(intent, "CANCELLED", "COMMIT_VALIDATION_" + result.status);
                sendNotifyTrade(result.status);
                closeTab();
                dispose();
            }
        } catch (Throwable t) {
            Logger.logException(Trade.class, new Exception("Error during trade commit execution", t));
            stateMachine.failCommit();
            if (intent != null) {
                ledgerSink.terminal(intent, "CANCELLED", "COMMIT_EXCEPTION");
            }
            sendNotifyTrade(FAIL_INVALID_OFFER);
            closeTab();
            dispose();
        }
    }

    private void sendNotifyTrade(byte status) {
        if (player1.idMark != null) player1.idMark.setLastTimeTrade(System.currentTimeMillis());
        if (player2.idMark != null) player2.idMark.setLastTimeTrade(System.currentTimeMillis());

        switch (status) {
            case SUCCESS:
                Service.gI().sendThongBao(player1, "Giao dịch thành công");
                Service.gI().sendThongBao(player2, "Giao dịch thành công");
                break;
            case FAIL_MAX_GOLD_PLAYER1:
                Service.gI().sendThongBao(player1, "Giao dịch thất bại do số lượng vàng sau giao dịch vượt tối đa");
                Service.gI().sendThongBao(player2,
                        "Giao dịch thất bại do số lượng vàng " + player1.name + " sau giao dịch vượt tối đa");
                break;
            case FAIL_MAX_GOLD_PLAYER2:
                Service.gI().sendThongBao(player2, "Giao dịch thất bại do số lượng vàng sau giao dịch vượt tối đa");
                Service.gI().sendThongBao(player1,
                        "Giao dịch thất bại do số lượng vàng " + player2.name + " sau giao dịch vượt tối đa");
                break;
            case FAIL_NOT_ENOUGH_BAG_P1:
                Service.gI().sendThongBao(player1, "Giao dịch thất bại vì " + player1.name + " không đủ chỗ chứa");
                Service.gI().sendThongBao(player2, "Giao dịch thất bại vì " + player1.name + " không đủ chỗ chứa");
                break;
            case FAIL_NOT_ENOUGH_BAG_P2:
                Service.gI().sendThongBao(player1, "Giao dịch thất bại vì " + player2.name + " không đủ chỗ chứa");
                Service.gI().sendThongBao(player2, "Giao dịch thất bại vì " + player2.name + " không đủ chỗ chứa");
                break;
            case FAIL_TRADE_LIMIT:
                Service.gI().sendThongBao(player1, "Giao dịch thất bại vì vật phẩm đã hết số lần giao dịch");
                Service.gI().sendThongBao(player2, "Giao dịch thất bại vì vật phẩm đã hết số lần giao dịch");
                break;
            case FAIL_INVALID_OFFER:
                Service.gI().sendThongBao(player1, "Giao dịch thất bại do vật phẩm hoặc vàng không hợp lệ");
                Service.gI().sendThongBao(player2, "Giao dịch thất bại do vật phẩm hoặc vàng không hợp lệ");
                break;
            case FAIL_ACTIVE:
                Service.gI().sendThongBao(player1, "Truy Cập: " + ServerManager.DOMAIN + "\n Để Mở Thành Viên");
                Service.gI().sendThongBao(player2, "Truy Cập: " + ServerManager.DOMAIN + "\n Để Mở Thành Viên");
                break;
        }
    }

    private void recordOwnershipAfterCommit(TradeCommitPlan.ImmutableTradeData data) {
        for (Item item : data.itemsTrade2) {
            try {
                CostumeCollectionService.gI().recordOwnership(player1, item);
            } catch (Exception ignored) {
            }
        }
        if (!player2.isBot) {
            for (Item item : data.itemsTrade1) {
                try {
                    CostumeCollectionService.gI().recordOwnership(player2, item);
                } catch (Exception ignored) {
                }
            }
        }
    }

    public boolean addItemBot(Player actor, Item it) {
        synchronized (tradeLock) {
            if (actor != player2 || !player2.isBot
                    || botOfferPrepared || !stateMachine.canModifyOffer(actor)
                    || it == null || !it.isNotNullItem() || it.quantity <= 0
                    || it.quantity > MAX_TRADE_ITEMS * 99) {
                return false;
            }
            int remaining = it.quantity;
            while (remaining > 0) {
                Item part = ItemService.gI().copyItem(it);
                part.quantity = Math.min(99, remaining);
                part.quantityGD = part.quantity;
                itemsTrade2.add(part);
                remaining -= part.quantity;
            }
            botOfferPrepared = true;
            return true;
        }
    }

    public void update() {
        if (this.start && Util.canDoWithTime(lastTimeStart, TIME_TRADE)) {
            if (this.stateMachine.expire()) {
                persistTerminal("EXPIRED", "TIMEOUT");
                String notifiText = "Giao dịch đã hết thời gian";
                Service.gI().sendThongBao(player1, notifiText);
                Service.gI().sendThongBao(player2, notifiText);
                closeTab();
                dispose();
            }
        }
    }
}
