package nro.models.shop_ky_gui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import nro.models.consts.ConstNpc;
import nro.models.item.Item;
import nro.models.item.Item.ItemOption;
import nro.models.map.service.NpcService;
import nro.models.network.Message;
import nro.models.player.Player;
import nro.models.server.Client;
import nro.models.services.InventoryService;
import nro.models.services.ItemService;
import nro.models.services.Service;
import nro.models.utils.Logger;

/**
 * SEC-04: Consignment shop protocol facade.
 * Coordinates packet decoding, delegates lifecycle mutations to ConsignPurchaseCoordinator,
 * and emits responses only after atomic commit.
 */
public class ConsignShopService {

    private static final int PAGE_SIZE = 20;
    private static final int MAX_WIRE_BYTE = Byte.MAX_VALUE;
    private static final int MAX_PACKET_PAYLOAD = 65_535;
    private static ConsignShopService instance;

    public static synchronized ConsignShopService gI() {
        if (instance == null) {
            instance = new ConsignShopService();
        }
        return instance;
    }

    public static synchronized void setInstanceForTest(ConsignShopService testInstance) {
        instance = testInstance;
    }

    private List<ConsignItem> getItemKyGui2(Player pl, byte tab, int from, int toExclusive) {
        List<ConsignItem> filtered = getItemKyGui(pl, tab);

        List<ConsignItem> result = new ArrayList<>();
        for (int i = Math.max(0, from); i < toExclusive && i < filtered.size(); i++) {
            result.add(filtered.get(i));
        }
        return result;
    }

    private List<ConsignItem> getItemKyGui(Player pl, byte tab) {
        return ConsignPurchaseCoordinator.gI().getActiveListingsSnapshot().stream()
                .filter(it -> it != null && it.tab == tab && it.isActive() && it.player_sell != pl.id)
                .sorted(Comparator.comparingInt((ConsignItem it) -> it.isUpTop).reversed()
                        .thenComparingInt(it -> it.id))
                .collect(Collectors.toList());
    }

    public List<ConsignItem> getItemKyGui() {
        return ConsignPurchaseCoordinator.gI().getActiveListingsSnapshot();
    }

    public boolean itemCanConsign(Item it) {
        return ConsignItemPolicy.canConsign(it);
    }

    public ConsignItem getItemBuy(int id) {
        return ConsignPurchaseCoordinator.gI().getListingSnapshot(id);
    }

    public ConsignItem getItemBuy(Player pl, int id) {
        ConsignItem it = ConsignPurchaseCoordinator.gI().getListingSnapshot(id);
        if (it != null && it.player_sell == pl.id) {
            return it;
        }
        return null;
    }

    public List<ConsignItem> getItemCanKiGui(Player pl) {
        List<ConsignItem> its = new ArrayList<>();
        // Add active/sold listings belonging to this player
        its.addAll(ConsignPurchaseCoordinator.gI().getPlayerListingsSnapshot(pl.id));

        // Add eligible items currently in bag
        if (pl.inventory != null && pl.inventory.itemsBag != null) {
            for (int i = 0; i < pl.inventory.itemsBag.size(); i++) {
                Item it = pl.inventory.itemsBag.get(i);
                if (itemCanConsign(it)) {
                    its.add(new ConsignItem(i, it.template.id, (int) pl.id, (byte) 4,
                            -1, -1, it.quantity, (byte) -1, it.itemOptions, false));
                }
            }
        }
        return its;
    }

    public int getMaxId() {
        int max = 0;
        for (ConsignItem it : ConsignPurchaseCoordinator.gI().getActiveListingsSnapshot()) {
            if (it != null && it.id > max) {
                max = it.id;
            }
        }
        return max;
    }

    // =========================================================================
    // Protocol Invocations
    // =========================================================================

    public void buyItem(Player pl, int id) {
        ConsignItem listing = getItemBuy(id);
        if (listing == null) {
            finishOperation(pl, ConsignPurchaseResult.fail(
                    ConsignPurchaseResult.Outcome.LISTING_NOT_FOUND, "Vật phẩm không tồn tại"));
            return;
        }
        byte moneyType = (byte) (listing.goldSell > 0 ? 0 : 1);
        int price = listing.goldSell > 0 ? listing.goldSell : listing.gemSell;
        buyItem(pl, id, moneyType, price);
    }

    public void buyItem(Player pl, int id, byte clientMoneyType, int clientPrice) {
        ConsignPurchaseResult result = ConsignPurchaseCoordinator.gI().purchase(pl, id, clientMoneyType, clientPrice);
        finishOperation(pl, result);
    }

    public void claimOrDel(Player pl, byte action, int id) {
        switch (action) {
            case 1: { // Hủy vật phẩm
                ConsignPurchaseResult result = ConsignPurchaseCoordinator.gI().cancel(pl, id);
                finishOperation(pl, result);
                break;
            }
            case 2: { // Nhận tiền
                ConsignPurchaseResult result = ConsignPurchaseCoordinator.gI().claim(pl, id);
                finishOperation(pl, result);
                break;
            }
            default:
                Service.gI().sendThongBao(pl, "Thao tác không hợp lệ");
                break;
        }
    }

    public void KiGui(Player pl, int id, int money, byte moneyType, int quantity) {
        ConsignPurchaseResult result = ConsignPurchaseCoordinator.gI().createListing(pl, id, moneyType, money, quantity);
        finishOperation(pl, result);
    }

    public void upItemToTop(Player pl, int id) {
        ConsignItem it = getItemBuy(id);
        if (it == null || !it.isActive()) {
            Service.gI().sendThongBao(pl, "Vật phẩm không tồn tại hoặc đã được bán");
            return;
        }
        if (it.player_sell != pl.id) {
            Service.gI().sendThongBao(pl, "Vật phẩm không thuộc quyền sở hữu");
            openShopKyGui(pl);
            return;
        }
        pl.idMark.setIdItemUpTop(id);
        NpcService.gI().createMenuConMeo(pl, ConstNpc.UP_TOP_ITEM, -1,
                "Bạn có muốn đưa vật phẩm ['" + ItemService.gI().createNewItem(it.itemId).template.name
                        + "'] của bản thân lên trang đầu?\nYêu cầu 2 thỏi vàng.",
                "Đồng ý", "Từ Chối");
    }

    public void StartupItemToTop(Player pl) {
        int listingId = pl.idMark.getIdItemUpTop();
        ConsignPurchaseResult result = ConsignPurchaseCoordinator.gI().upTop(pl, listingId);
        finishOperation(pl, result);
    }

    // =========================================================================
    // Wire Responses: Command -44 and Command -100
    // =========================================================================

    public void openShopKyGui(Player pl) {
        Message msg = null;
        try {
            msg = new Message(-44);
            msg.writer().writeByte(2); // b35 = 2
            msg.writer().writeByte(5); // 5 tabs
            for (byte i = 0; i < 5; i++) {
                if (i == 4) { // Tab hành trang ký gửi
                    msg.writer().writeUTF(ConsignShopManager.gI().tabName[i]);
                    msg.writer().writeByte(0); // max page
                    List<ConsignItem> canConsign = getItemCanKiGui(pl);
                    if (canConsign.size() > MAX_WIRE_BYTE) {
                        Logger.error("[SEC-04] Owner consignment tab truncated to 127 entries, playerId=" + pl.id
                                + ", available=" + canConsign.size());
                        canConsign = new ArrayList<>(canConsign.subList(0, MAX_WIRE_BYTE));
                    }
                    msg.writer().writeByte(canConsign.size());
                    for (int j = 0; j < canConsign.size(); j++) {
                        ConsignItem itk = canConsign.get(j);
                        if (itk == null) {
                            continue;
                        }
                        Item it = ItemService.gI().createNewItem(itk.itemId);
                        it.itemOptions.clear();
                        if (itk.options.isEmpty()) {
                            it.itemOptions.add(new ItemOption(73, 0));
                        } else {
                            it.itemOptions.addAll(ConsignItem.deepCopyOptions(itk.options));
                        }
                        msg.writer().writeShort(it.template.id);
                        msg.writer().writeShort(itk.id);
                        msg.writer().writeInt(itk.goldSell);
                        msg.writer().writeInt(itk.gemSell);

                        if (itk.tab == 4) {
                            // Bag item ready to consign
                            msg.writer().writeByte(0); // buy type
                        } else if (itk.isSold() || itk.isBuy) {
                            // Sold item ready for seller to claim proceeds
                            msg.writer().writeByte(2);
                        } else {
                            // Active item ready for seller to cancel
                            msg.writer().writeByte(1);
                        }

                        msg.writer().writeInt(itk.quantity);
                        msg.writer().writeByte(1); // isMe
                        List<Item.ItemOption> itemOptions = validVisibleOptions(it);
                        requireWireByteCount(itemOptions.size(), "item options");
                        msg.writer().writeByte(itemOptions.size());
                        for (Item.ItemOption option : itemOptions) {
                            msg.writer().writeByte(option.optionTemplate.id);
                            msg.writer().writeShort(option.param);
                        }
                        msg.writer().writeByte(0);
                        msg.writer().writeByte(0);
                    }
                } else {
                    List<ConsignItem> items = getItemKyGui(pl, i);
                    List<ConsignItem> itemsSend = getItemKyGui2(pl, i, 0, PAGE_SIZE);
                    msg.writer().writeUTF(ConsignShopManager.gI().tabName[i]);
                    msg.writer().writeByte(pageCount(items.size())); // max page
                    msg.writer().writeByte(itemsSend.size());
                    for (int j = 0; j < itemsSend.size(); j++) {
                        ConsignItem itk = itemsSend.get(j);
                        Item it = ItemService.gI().createNewItem(itk.itemId);
                        it.itemOptions.clear();
                        if (itk.options.isEmpty()) {
                            it.itemOptions.add(new ItemOption(73, 0));
                        } else {
                            it.itemOptions.addAll(ConsignItem.deepCopyOptions(itk.options));
                        }
                        msg.writer().writeShort(it.template.id);
                        msg.writer().writeShort(itk.id);
                        msg.writer().writeInt(itk.goldSell);
                        msg.writer().writeInt(itk.gemSell);
                        msg.writer().writeByte(0); // buy type
                        msg.writer().writeInt(itk.quantity);
                        msg.writer().writeByte(itk.player_sell == pl.id ? 1 : 0); // isMe
                        List<Item.ItemOption> itemOptions = validVisibleOptions(it);
                        requireWireByteCount(itemOptions.size(), "item options");
                        msg.writer().writeByte(itemOptions.size());
                        for (Item.ItemOption option : itemOptions) {
                            msg.writer().writeByte(option.optionTemplate.id);
                            msg.writer().writeShort(option.param);
                        }
                        msg.writer().writeByte(0);
                        msg.writer().writeByte(0);
                    }
                }
            }
            requirePacketSize(msg);
            pl.sendMessage(msg);
        } catch (Exception e) {
            Logger.logException(ConsignShopService.class, e);
            Service.gI().sendThongBao(pl, "Dữ liệu ký gửi vượt giới hạn giao thức; vui lòng thử lại");
        } finally {
            if (msg != null) {
                msg.cleanup();
            }
        }
    }

    public void openShopKyGui(Player pl, byte index, int page) {
        if (index < 0 || index >= 4 || page < 0) {
            return;
        }
        List<ConsignItem> items = getItemKyGui(pl, index);
        int pageCount = pageCount(items.size());
        if (page >= pageCount || page >= MAX_WIRE_BYTE) {
            return;
        }
        Message msg = null;
        try {
            msg = new Message(-100);
            msg.writer().writeByte(index);
            int from = page * PAGE_SIZE;
            List<ConsignItem> itemsSend = getItemKyGui2(pl, index, from, from + PAGE_SIZE);
            msg.writer().writeByte(pageCount); // max page
            msg.writer().writeByte(page);
            msg.writer().writeByte(itemsSend.size());
            for (int j = 0; j < itemsSend.size(); j++) {
                ConsignItem itk = itemsSend.get(j);
                Item it = ItemService.gI().createNewItem(itk.itemId);
                it.itemOptions.clear();
                if (itk.options.isEmpty()) {
                    it.itemOptions.add(new ItemOption(73, 0));
                } else {
                    it.itemOptions.addAll(ConsignItem.deepCopyOptions(itk.options));
                }
                msg.writer().writeShort(it.template.id);
                msg.writer().writeShort(itk.id);
                msg.writer().writeInt(itk.goldSell);
                msg.writer().writeInt(itk.gemSell);
                msg.writer().writeByte(0); // buy type
                if (pl.getSession().version >= 222) {
                    msg.writer().writeInt(itk.quantity);
                } else {
                    msg.writer().writeByte(itk.quantity);
                }
                msg.writer().writeByte(itk.player_sell == pl.id ? 1 : 0); // isMe
                List<Item.ItemOption> itemOptions = validVisibleOptions(it);
                requireWireByteCount(itemOptions.size(), "item options");
                msg.writer().writeByte(itemOptions.size());
                for (Item.ItemOption option : itemOptions) {
                    msg.writer().writeByte(option.optionTemplate.id);
                    msg.writer().writeShort(option.param);
                }
                msg.writer().writeByte(0);
            }
            requirePacketSize(msg);
            pl.sendMessage(msg);
        } catch (Exception e) {
            Logger.logException(ConsignShopService.class, e);
            Service.gI().sendThongBao(pl, "Dữ liệu ký gửi vượt giới hạn giao thức; vui lòng thử lại");
        } finally {
            if (msg != null) {
                msg.cleanup();
            }
        }
    }

    private void finishOperation(Player player, ConsignPurchaseResult result) {
        if (result.isSuccess()) {
            InventoryService.gI().sendItemBags(player);
            Service.gI().sendMoney(player);
        }
        Service.gI().sendThongBao(player, result.getMessage());
        if (result.getOutcome() == ConsignPurchaseResult.Outcome.PERSISTENCE_UNKNOWN) {
            Client.gI().kickSession(player.getSession(), nro.models.network.SessionCloseCause.INTERNAL_ERROR);
            return;
        }
        openShopKyGui(player);
    }

    private List<Item.ItemOption> validVisibleOptions(Item item) {
        List<Item.ItemOption> visible = ItemService.gI().getVisibleItemOptions(item);
        if (visible == null || visible.isEmpty()) {
            return new ArrayList<>();
        }
        return visible.stream()
                .filter(option -> option != null && option.optionTemplate != null)
                .collect(Collectors.toList());
    }

    private int pageCount(int itemCount) {
        int pages = Math.max(1, (itemCount + PAGE_SIZE - 1) / PAGE_SIZE);
        return Math.min(MAX_WIRE_BYTE, pages);
    }

    private void requireWireByteCount(int count, String field) throws IOException {
        if (count < 0 || count > MAX_WIRE_BYTE) {
            throw new IOException(field + " count exceeds signed-byte protocol limit: " + count);
        }
    }

    private void requirePacketSize(Message message) throws IOException {
        int size = message.getData().length;
        if (size > MAX_PACKET_PAYLOAD) {
            throw new IOException("Consignment packet exceeds 65535-byte payload limit: " + size);
        }
    }
}
