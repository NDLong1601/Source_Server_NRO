package nro.models.services;

import nro.models.data.DataGame;
import nro.models.item.Item;
import nro.models.item.Item.ItemOption;
import nro.models.combine.NangCapNhan;
import nro.models.npc.MabuEgg;
import nro.models.player.Inventory;
import nro.models.player.PlayerConfig;
import nro.models.player.Pet;
import nro.models.player.PetConfig;
import nro.models.player.Player;
import nro.models.network.Message;
import nro.models.services.ItemService;
import nro.models.services.Service;
import nro.models.services_dungeon.NgocRongNamecService;
import nro.models.map.service.ChangeMapService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import nro.models.consts.ConstTaskBadges;
import nro.models.services_dungeon.BlackBallWarService;
import nro.models.map.service.ItemMapService;
import nro.models.task.BadgesTaskService;
import nro.models.utils.Util;

public class InventoryService {

    public static final int PLAYER_BODY_SLOT_COUNT = 14;
    public static final int PLAYER_TRAIN_ARMOR_SLOT = 6;
    public static final int PLAYER_FOLLOW_PET_SLOT = 7;
    public static final int PLAYER_BACK_SLOT = 8;
    public static final int PLAYER_MOUNT_SLOT = 9;
    public static final int PLAYER_RING_SLOT = 10;
    public static final int PLAYER_SKILL_BOOK_SLOT = 11;
    public static final int PLAYER_AURA_SLOT = 12;
    public static final int PLAYER_TITLE_SLOT = 13;

    /**
     * Aura templates are intentionally kept in one contiguous range. Item
     * templates are indexed by ID in this server, so these IDs must stay in
     * sync with the data seed and the Admin Aura tab.
     */
    public static final int AURA_ITEM_ID_START = 2154;
    public static final int AURA_ITEM_ID_END = 2217;
    public static final int LEGACY_AURA_ITEM_ID = 1230;
    public static final int LEGACY_AURA_ID = 3;

    public static final int PET_COSTUME_SLOT = 5;
    public static final int PET_RING_SLOT = 6;
    public static final int PET_BACK_SLOT = 7;
    public static final int PET_SKILL_BOOK_SLOT = 8;

    private static InventoryService I;

    public static InventoryService gI() {
        if (InventoryService.I == null) {
            InventoryService.I = new InventoryService();
        }
        return InventoryService.I;
    }

    private void __________________Tìm_kiếm_item_____________________________() {
        //**********************************************************************
    }

    public Item findItem(List<Item> list, int tempId) {
        try {
            for (Item item : list) {
                if (item.isNotNullItem() && item.template.id == tempId) {
                    return item;
                }
            }
        } catch (Exception e) {
        }
        return null;
    }

    public Item findItemBody(Player player, int tempId) {
        return this.findItem(player.inventory.itemsBody, tempId);
    }

    public Item findItemBag(Player player, int tempId) {
        return this.findItem(player.inventory.itemsBag, tempId);
    }

    public Item findItemBox(Player player, int tempId) {
        return this.findItem(player.inventory.itemsBox, tempId);
    }

    public boolean isExistItem(List<Item> list, int tempId) {
        try {
            return this.findItem(list, tempId) != null;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isExistItemBody(Player player, int tempId) {
        return this.isExistItem(player.inventory.itemsBody, tempId);
    }

    public boolean isExistItemBag(Player player, int tempId) {
        return this.isExistItem(player.inventory.itemsBag, tempId);
    }

    public boolean isExistItemBox(Player player, int tempId) {
        return this.isExistItem(player.inventory.itemsBox, tempId);
    }

    private void __________________Sao_chép_danh_sách_item__________________() {
        //**********************************************************************
    }

    public List<Item> copyList(List<Item> items) {
        List<Item> list = new ArrayList<>();
        for (Item item : items) {
            list.add(ItemService.gI().copyItem(item));
        }
        return list;
    }

    public List<Item> copyItemsBody(Player player) {
        return copyList(player.inventory.itemsBody);
    }

    public List<Item> copyItemsBag(Player player) {
        return copyList(player.inventory.itemsBag);
    }

    public List<Item> copyItemsBox(Player player) {
        return copyList(player.inventory.itemsBox);
    }

    private void __________________Vứt_bỏ_item______________________________() {
        //**********************************************************************
    }

    public void throwItem(Player player, int where, int index) {
        Item itemThrow = null;
        if (where == 0) {
            itemThrow = player.inventory.itemsBody.get(index);
            removeItemBody(player, index);
            sendItemBody(player);
            Service.gI().Send_Caitrang(player);
        } else if (where == 1) {
            itemThrow = player.inventory.itemsBag.get(index);
            if (itemThrow.template != null && itemThrow.template.id == 570) {
                Service.gI().sendThongBao(player, "Không thể bỏ vật phẩm này.");
                return;
            }
            if (itemThrow.template != null && itemThrow.template.id != 457) {
                removeItemBag(player, index);
                sortItems(player.inventory.itemsBag);
                sendItemBags(player);
            } else {
                Service.gI().sendThongBao(player, "Bỏ cái địt mẹ mày thằng ngu");
            }
        }
        if (itemThrow == null) {
            return;
        }
    }

    private void __________________Xoá_bỏ_item______________________________() {
        //**********************************************************************
    }

    public void removeItem(List<Item> items, int index) {
        Item item = ItemService.gI().createItemNull();
        items.set(index, item);
    }

    public void removeItem(List<Item> items, Item item) {
        if (item == null) {
            return;
        }
        Item it = ItemService.gI().createItemNull();
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).equals(item)) {
                items.set(i, it);
                item.dispose();
                break;
            }
        }
    }

    public void removeItemBag(Player player, int index) {
        this.removeItem(player.inventory.itemsBag, index);
    }

    public void removeItemBag(Player player, Item item) {
        this.removeItem(player.inventory.itemsBag, item);
    }

    public void removeItemBody(Player player, int index) {
        this.removeItem(player.inventory.itemsBody, index);
    }

    public void removeItemPetBody(Player player, int index) {
        this.removeItemBody(player.pet, index);
    }

    public void removeItemBox(Player player, int index) {
        this.removeItem(player.inventory.itemsBox, index);
    }

    private void __________________Giảm_số_lượng_item_______________________() {
        //**********************************************************************
    }

    public void subQuantityItemsBag(Player player, Item item, int quantity) {
        subQuantityItem(player.inventory.itemsBag, item, quantity);
    }

    public void subQuantityItemsBody(Player player, Item item, int quantity) {
        subQuantityItem(player.inventory.itemsBody, item, quantity);
    }

    public void subQuantityItemsBox(Player player, Item item, int quantity) {
        subQuantityItem(player.inventory.itemsBox, item, quantity);
    }

    public void subQuantityItem(List<Item> items, Item item, int quantity) {
        if (item != null) {
            for (Item it : items) {
                if (item.equals(it)) {
                    it.quantity -= quantity;
                    if (it.quantity <= 0) {
                        this.removeItem(items, item);
                    } else {
                        syncQuantityOption(it);
                    }
                    break;
                }
            }
        }
    }

    private void __________________Sắp_xếp_danh_sách_item___________________() {
        //**********************************************************************
    }

    public void sortItems(List<Item> list) {
        for (int i = 0; i < list.size() - 1; i++) {
            Item current = list.get(i);
            if (current == null || !current.isNotNullItem()) {
                int indexSwap = -1;
                for (int j = i + 1; j < list.size(); j++) {
                    Item candidate = list.get(j);
                    if (candidate != null && candidate.isNotNullItem()) {
                        indexSwap = j;
                        break;
                    }
                }
                if (indexSwap != -1) {
                    list.set(i, list.get(indexSwap));
                    list.set(indexSwap, ItemService.gI().createItemNull());
                } else {
                    break;
                }
            }
        }
    }

    public Item finditemBongHoa(Player player, int soluong) {
        for (Item item : player.inventory.itemsBag) {
            if (item.isNotNullItem() && (item.template.id == 589) && item.quantity >= soluong) {
                return item;
            }
        }
        return null;
    }

    public void sortItemv2(List<Item> items) {
        int index = 0;
        for (Item item : items) {
            if (item != null && item.quantity > 0) {
                items.set(index, item);
                index++;
            }
        }
        for (int i = index; i < items.size(); i++) {
            items.set(i, null);
        }
    }

    private void __________________Thao_tác_tháo_mặc_item___________________() {
        //**********************************************************************
    }

    private Item putItemBag(Player player, Item item) {
        for (int i = 0; i < player.inventory.itemsBag.size(); i++) {
            if (!player.inventory.itemsBag.get(i).isNotNullItem()) {
                player.inventory.itemsBag.set(i, item);
                Item sItem = ItemService.gI().createItemNull();
                return sItem;
            }
        }
        return item;
    }

    private Item putItemBox(Player player, Item item) {
        for (int i = 0; i < player.inventory.itemsBox.size(); i++) {
            if (!player.inventory.itemsBox.get(i).isNotNullItem()) {
                player.inventory.itemsBox.set(i, item);
                Item sItem = ItemService.gI().createItemNull();
                return sItem;
            }
        }
        return item;
    }

    public static boolean isPlayerSkillBook(Item item) {
        if (item == null || !item.isNotNullItem() || item.template.type != 25) {
            return false;
        }
        return switch (item.template.id) {
            case 1044, 1211, 1212 -> true;
            default -> false;
        };
    }

    public static boolean isPetSkillBook(Item item) {
        if (item == null || !item.isNotNullItem() || item.template.type != 25) {
            return false;
        }
        return switch (item.template.id) {
            case 1278, 1279, 1280 -> true;
            default -> false;
        };
    }

    public static boolean isAuraItem(Item item) {
        if (item == null || !item.isNotNullItem() || item.template == null || item.template.type != 11) {
            return false;
        }
        int templateId = item.template.id;
        return templateId == LEGACY_AURA_ITEM_ID
                || (templateId >= AURA_ITEM_ID_START && templateId <= AURA_ITEM_ID_END);
    }

    /**
     * Aura items store their client aura ID in {@code item_template.part}.
     * This field is safe here because the recognized templates are dedicated
     * to the aura slot and never use the normal type-11 back-item rendering.
     */
    public static int getAuraId(Item item) {
        if (!isAuraItem(item)) {
            return -1;
        }
        if (item.template.id == LEGACY_AURA_ITEM_ID) {
            return LEGACY_AURA_ID;
        }
        return item.template.part;
    }

    private int getPlayerBodySlot(Item item) {
        return switch (item.template.type) {
            case 0, 1, 2, 3, 4, 5 -> item.template.type;
            case 32 -> PLAYER_TRAIN_ARMOR_SLOT;
            case 27 -> PLAYER_FOLLOW_PET_SLOT;
            case 11 -> isAuraItem(item) ? PLAYER_AURA_SLOT : PLAYER_BACK_SLOT;
            case 23, 24 -> PLAYER_MOUNT_SLOT;
            case 25 -> isPlayerSkillBook(item) ? PLAYER_SKILL_BOOK_SLOT
                    : (isPetSkillBook(item) ? -1 : PLAYER_RING_SLOT);
            case 36 -> PLAYER_TITLE_SLOT;
            default -> -1;
        };
    }

    private int getPetBodySlot(Item item) {
        return switch (item.template.type) {
            case 0, 1, 2, 3, 4, 5 -> item.template.type;
            case 11 -> PET_BACK_SLOT;
            case 25 -> NangCapNhan.isRing(item) ? PET_RING_SLOT
                    : (isPetSkillBook(item) ? PET_SKILL_BOOK_SLOT : -1);
            default -> -1;
        };
    }

    /**
     * Chuyển dữ liệu trang bị đệ tử từ ánh xạ cũ sang bố cục mới:
     * 5 cải trang, 6 nhẫn, 7 đeo lưng/cờ, 8 sách tuyệt kỹ.
     */
    public void normalizePetEquipmentSlots(Player master) {
        if (master == null || master.pet == null
                || master.pet.inventory == null
                || master.pet.inventory.itemsBody.size() <= PET_SKILL_BOOK_SLOT) {
            return;
        }

        List<Item> body = master.pet.inventory.itemsBody;
        List<Item> oldExtraItems = new ArrayList<>();
        for (int slot = PET_RING_SLOT; slot <= PET_SKILL_BOOK_SLOT; slot++) {
            Item item = body.get(slot);
            if (item != null && item.isNotNullItem()) {
                oldExtraItems.add(item);
            }
            body.set(slot, ItemService.gI().createItemNull());
        }

        List<Item> overflowItems = new ArrayList<>();
        for (Item item : oldExtraItems) {
            int targetSlot = getPetBodySlot(item);
            if (targetSlot >= PET_RING_SLOT && targetSlot <= PET_SKILL_BOOK_SLOT
                    && !body.get(targetSlot).isNotNullItem()) {
                body.set(targetSlot, item);
            } else {
                overflowItems.add(item);
            }
        }

        for (Item item : overflowItems) {
            Item remaining = putItemBag(master, item);
            if (remaining.isNotNullItem()) {
                remaining = putItemBox(master, remaining);
            }
            if (remaining.isNotNullItem()) {
                boolean restored = false;
                for (int slot = PET_RING_SLOT; slot <= PET_SKILL_BOOK_SLOT; slot++) {
                    if (!body.get(slot).isNotNullItem()) {
                        body.set(slot, remaining);
                        restored = true;
                        break;
                    }
                }
                if (!restored) {
                    body.add(remaining);
                }
            }
        }
    }

    /**
     * Extends and normalizes the player's equipment layout through slot 13.
     * Special items from the old shared slots are moved without deleting items
     * when the destination is already occupied.
     */
    public void normalizePlayerEquipmentSlots(Player player) {
        if (player == null || player.inventory == null) {
            return;
        }
        List<Item> body = player.inventory.itemsBody;
        while (body.size() < PLAYER_BODY_SLOT_COUNT) {
            body.add(ItemService.gI().createItemNull());
        }

        for (int sourceSlot = 0; sourceSlot < body.size(); sourceSlot++) {
            Item item = body.get(sourceSlot);
            if (item == null || !item.isNotNullItem()) {
                continue;
            }

            if (isPetSkillBook(item)) {
                if (moveBookToPet(player, item) || storePlayerEquipmentItem(player, item)) {
                    body.set(sourceSlot, ItemService.gI().createItemNull());
                }
                continue;
            }

            int targetSlot = -1;
            if (isPlayerSkillBook(item)) {
                targetSlot = PLAYER_SKILL_BOOK_SLOT;
            } else if (isAuraItem(item)) {
                targetSlot = PLAYER_AURA_SLOT;
            } else if (item.template.type == 36) {
                targetSlot = PLAYER_TITLE_SLOT;
            }

            if (targetSlot < 0 || targetSlot == sourceSlot) {
                continue;
            }
            Item targetItem = body.get(targetSlot);
            if (targetItem == null || !targetItem.isNotNullItem()) {
                body.set(targetSlot, item);
                body.set(sourceSlot, ItemService.gI().createItemNull());
            } else if (storePlayerEquipmentItem(player, item)) {
                body.set(sourceSlot, ItemService.gI().createItemNull());
            }
        }
    }

    private boolean moveBookToPet(Player player, Item item) {
        if (player.pet == null
                || !PetConfig.isTypeAllowed("pet.equipment.vipTypes", player.pet.typePet, 2, 3, 4)) {
            return false;
        }
        while (player.pet.inventory.itemsBody.size() <= PET_SKILL_BOOK_SLOT) {
            player.pet.inventory.itemsBody.add(ItemService.gI().createItemNull());
        }
        Item equippedBook = player.pet.inventory.itemsBody.get(PET_SKILL_BOOK_SLOT);
        if (equippedBook != null && equippedBook.isNotNullItem()) {
            return false;
        }
        player.pet.inventory.itemsBody.set(PET_SKILL_BOOK_SLOT, item);
        return true;
    }

    private boolean storePlayerEquipmentItem(Player player, Item item) {
        Item remaining = putItemBag(player, item);
        if (!remaining.isNotNullItem()) {
            return true;
        }
        remaining = putItemBox(player, remaining);
        return !remaining.isNotNullItem();
    }

    public Item putItemBody(Player player, Item item) {
        byte type = item.getType();
        Item sItem = item;
        if (!item.isNotNullItem()) {
            return sItem;
        }

        // Kiểm tra các loại item hợp lệ
        switch (item.template.type) {
            case 0, 1, 2, 3, 4, 5, 32, 23, 24, 11, 27, 25, 36 -> {
            }
            default -> {
                Service.gI().sendThongBaoOK(player.isPet ? ((Pet) player).master : player, "Trang bị không phù hợp!1");
                return sItem;
            }
        }

        // Kiểm tra giới tính của vật phẩm
        if (item.template.gender < 3 && item.template.gender != player.gender) {
            Service.gI().sendThongBaoOK(player.isPet ? ((Pet) player).master : player, "Trang bị không phù hợp!");
            return sItem;
        }

        // Kiểm tra các vật phẩm có ID đặc biệt
        if (item.getId() == 691 || item.getId() == 692 || item.getId() == 693) {
            List<Item> itemsBody = player.inventory.itemsBody;
            if (itemsBody.get(0).isNotNullItem() && itemsBody.get(5).isNotNullItem()) {
                Service.gI().sendThongBaoOK(player.isPet ? ((Pet) player).master : player, "Vui lòng cởi áo để có thể sử dụng!");
                return sItem;
            }
        }

        // Kiểm tra yêu cầu sức mạnh
        long powerRequire = item.template.strRequire;
        for (Item.ItemOption io : item.itemOptions) {
            if (io.optionTemplate.id == 21) {
                powerRequire = io.param * 1000000000L;
                break;
            }
        }
        if (player.nPoint.power < powerRequire) {
            Service.gI().sendThongBaoOK(player.isPet ? ((Pet) player).master : player, "Sức mạnh không đủ yêu cầu!");
            return sItem;
        }
        ItemService.gI().revealBonusOptions(item);
        if (item.template.type == 32) {
            ItemService.gI().ensureTrainArmorTimeOption(item);
        }
        handleOption210(item);
        checkOption231(item);
        int index = -1;
        if (player.isPet) {
            index = getPetBodySlot(item);
        } else {
            index = getPlayerBodySlot(item);
        }
        if (player.isPet && (item.template.type == 11 || item.template.type == 25)) {
            Pet pet = (Pet) player;
            if (!PetConfig.isTypeAllowed("pet.equipment.vipTypes", pet.typePet, 2, 3, 4)) {
                Player recipient = pet.master != null ? pet.master : player;
                Service.gI().sendThongBaoOK(recipient, "Chỉ đệ tử vip mới sử dụng được vật phẩm này!");
                return sItem;
            }
        }

        if (player.isPet && (item.template.type == 23 || item.template.type == 24
                || item.template.type == 27 || item.template.type == 32 || index < 0)) {
            Player recipient = ((Pet) player).master;
            if (recipient == null) {
                recipient = player;
            }
            Service.gI().sendThongBaoOK(recipient, "Đệ tử không thể sử dụng vật phẩm này!");
            return sItem;
        }

        if (!player.isPet && index < 0) {
            Service.gI().sendThongBaoOK(player, "Vật phẩm này chỉ có thể trang bị cho đệ tử!");
            return sItem;
        }

        if (player.isPet) {
            Pet pet = (Pet) player;
            while (pet.inventory.itemsBody.size() <= index) {
                pet.inventory.itemsBody.add(ItemService.gI().createItemNull());
            }
        } else {
            while (player.inventory.itemsBody.size() <= index) {
                player.inventory.itemsBody.add(ItemService.gI().createItemNull());
            }
        }

        sItem = player.inventory.itemsBody.get(index);
        player.inventory.itemsBody.set(index, item);
        return sItem;

    }

    public void itemBagToBody(Player player, int index) {
        if (index < 0) {
            Service.gI().sendThongBao(player, "Không thể thực hiện");
            return;
        }
        Item item = player.inventory.itemsBag.get(index);
        if (item.isNotNullItem()) {
            boolean isAura = isAuraItem(item);
            player.inventory.itemsBag.set(index, putItemBody(player, item));
            sendItemBags(player);
            sendItemBody(player);
            Service.gI().point(player);
            Service.gI().Send_Caitrang(player);
            if (isAura && player.zone != null) {
                // The costume packet does not include idauraeff. Re-send the
                // regular player/map data so the wearer and nearby players
                // receive the new aura immediately instead of on map change.
                Service.gI().player(player);
                player.zone.load_Me_To_Another(player);
                player.zone.load_Another_To_Me(player);
            }
        }
    }

    public void itemBodyToBag(Player player, int index) {
        Item item = player.inventory.itemsBody.get(index);
        if (item.isNotNullItem()) {
            if (index == PLAYER_FOLLOW_PET_SLOT && !player.isPet && item.template.type != 25) {
                if (player.newPet != null) {
                    ChangeMapService.gI().exitMap(player.newPet);
                    player.newPet.dispose();
                    player.newPet = null;
                }
            }
            player.inventory.itemsBody.set(index, putItemBag(player, item));
            sendItemBags(player);
            sendItemBody(player);
            Service.gI().player(player);
            player.zone.load_Me_To_Another(player);
            player.zone.load_Another_To_Me(player);
            Service.gI().Send_Caitrang(player);
            Service.gI().sendFlagBag(player);
            Service.gI().point(player);
        }
    }

    public void itemBagToPetBody(Player player, int index) {
        try {
            long minPower = PetConfig.getLong("pet.equipment.minPower", 1_500_000L, 0L, Long.MAX_VALUE);
            if (player.pet != null && player.pet.nPoint.power >= minPower) {
                Item item = player.inventory.itemsBag.get(index);
                if (item.isNotNullItem()) {
                    Item itemSwap = putItemBody(player.pet, item);
                    player.inventory.itemsBag.set(index, itemSwap);
                    sendItemBags(player);
                    sendItemBody(player);
                    if (!itemSwap.equals(item)) {
                        Service.gI().point(player);
                        Service.gI().showInfoPet(player);
                    }
                    Service.gI().Send_Caitrang(player.pet);
                    Service.gI().Send_Caitrang(player);
                    if (player.pet.zone != null) {
                        Service.gI().sendFlagBag(player.pet);
                    }
                }
            } else {
                Service.gI().sendThongBao(player, "Đệ tử phải đạt " + Util.numberToMoney(minPower)
                        + " sức mạnh mới có thể mặc");
            }
        } catch (Exception E) {
            Service.gI().sendThongBao(player, "Không thể thực hiện");
        }
    }

    public void itemPetBodyToBag(Player player, int index) {
        Item item = player.pet.inventory.itemsBody.get(index);
        if (item.isNotNullItem()) {
            player.pet.inventory.itemsBody.set(index, putItemBag(player, item));
            sendItemBags(player);
            sendItemBody(player);
            Service.gI().point(player);
            Service.gI().Send_Caitrang(player.pet);
            Service.gI().Send_Caitrang(player);
            if (player.pet.zone != null) {
                Service.gI().sendFlagBag(player.pet);
            }
            Service.gI().showInfoPet(player);
        }
    }

    public void itemBoxToBodyOrBag(Player player, int index) {
        if (index < 0) {
            Service.gI().sendThongBao(player, "Không thể thực hiện");
            return;
        }
        Item item = player.inventory.itemsBox.get(index);
        if (item.isNotNullItem()) {
            boolean done = false;
            if (item.template.type >= 0 && item.template.type <= 5 || item.template.type == 32) {
                Item itemBody = player.inventory.itemsBody.get(item.template.type == 32 ? 6 : item.template.type);
                if (!itemBody.isNotNullItem()) {
                    if (item.template.gender == player.gender || item.template.gender == 3) {
                        long powerRequire = item.template.strRequire;
                        for (Item.ItemOption io : item.itemOptions) {
                            if (io.optionTemplate.id == 21) {
                                powerRequire = io.param * 1000000000L;
                                break;
                            }
                        }
                        if (powerRequire <= player.nPoint.power) {
                            player.inventory.itemsBody.set(item.template.type == 32 ? 6 : item.template.type, item);
                            player.inventory.itemsBox.set(index, itemBody);
                            done = true;

                            sendItemBody(player);
                            Service.gI().point(player);
                            Service.gI().Send_Caitrang(player);
                        }
                    }
                }
            }
            if (!done) {
                if (addItemBag(player, item)) {

                    if (item.quantity == 0) {
                        Item sItem = ItemService.gI().createItemNull();
                        player.inventory.itemsBox.set(index, sItem);
                    }
                    sendItemBags(player);
                }
            }
        }
    }

    public void itemBagToBox(Player player, int index) {
        if (index < 0 || index >= player.inventory.itemsBag.size()) {
            Service.gI().sendThongBao(player, "Không thể thực hiện");
            return;
        }
        Item item = player.inventory.itemsBag.get(index);
        if (item != null && item.isNotNullItem()) {
            if (item.template.id == 457) {
                Service.gI().sendThongBao(player, "Không thể cất vàng vào rương");
                return;
            }

            boolean added = addItemBox(player, item);

            if (added) {
                if (item.quantity == 0) {
                    Item sItem = ItemService.gI().createItemNull();
                    player.inventory.itemsBag.set(index, sItem);
                }
                sortItems(player.inventory.itemsBag);
                sendItemBags(player);
                sendItemBox(player);
            }
        }
    }

    public void itemBodyToBox(Player player, int index) {
        if (index < 0 || index >= player.inventory.itemsBody.size()) {
            Item item = player.inventory.itemsBody.get(index);
            if (item.isNotNullItem()) {
                player.inventory.itemsBody.set(index, putItemBox(player, item));
                sortItems(player.inventory.itemsBag);
                sendItemBody(player);
                sendItemBox(player);
                Service.gI().point(player);
                Service.gI().Send_Caitrang(player);
            }
        }
    }

    private void __________________Gửi_danh_sách_item_cho_người_chơi________() {
        //**********************************************************************
    }

    public void sendItemBags(Player player) {
        sortItems(player.inventory.itemsBag);
        DataGame.preloadPlayerBagIcons(player);
        Message msg;
        try {
            msg = new Message(-36);
            msg.writer().writeByte(0);
            msg.writer().writeByte(player.inventory.itemsBag.size());
            for (int i = 0; i < player.inventory.itemsBag.size(); i++) {
                Item item = player.inventory.itemsBag.get(i);
                if (!item.isNotNullItem()) {
                    continue;
                }
                msg.writer().writeShort(item.template.id);
                msg.writer().writeInt(item.quantity);
                msg.writer().writeUTF(item.getInfo());
                msg.writer().writeUTF(item.getContent());
                List<Item.ItemOption> itemOptions = ItemService.gI().getVisibleItemOptions(item);
                msg.writer().writeByte(itemOptions.size()); //options
                for (int j = 0; j < itemOptions.size(); j++) {
                    if (itemOptions.get(j).optionTemplate.id == 213) {
                        int opId = 213;
                        int param = itemOptions.get(j).param;
                        if (param > 1_000_000) {
                            opId = 223;
                            param /= 1_000_000;
                        } else if (param > 1000) {
                            opId = 222;
                            param /= 1000;
                        }
                        msg.writer().writeByte(opId);
                        msg.writer().writeShort(param);
                    } else {
                        msg.writer().writeByte(itemOptions.get(j).optionTemplate.id);
                        msg.writer().writeShort(itemOptions.get(j).param);
                    }
                }
            }

            player.sendMessage(msg);
            msg.cleanup();
        } catch (Exception e) {
        }
    }

    public void sendItemBody(Player player) {
        Message msg;
        try {
            msg = new Message(-37);
            msg.writer().writeByte(0);
            msg.writer().writeShort(player.getHead());
            msg.writer().writeByte(player.inventory.itemsBody.size());
            for (Item item : player.inventory.itemsBody) {
                if (!item.isNotNullItem()) {
                    msg.writer().writeShort(-1);
                } else {
                    msg.writer().writeShort(item.template.id);
                    msg.writer().writeInt(item.quantity);
                    msg.writer().writeUTF(item.getInfo());
                    msg.writer().writeUTF(item.getContent());
                    List<Item.ItemOption> itemOptions = ItemService.gI().getVisibleItemOptions(item);
                    msg.writer().writeByte(itemOptions.size());
                    for (Item.ItemOption itemOption : itemOptions) {
                        if (itemOption.optionTemplate.id == 213) {
                            int opId = 213;
                            int param = itemOption.param;
                            if (param > 1_000_000) {
                                opId = 223;
                                param /= 1_000_000;
                            } else if (param > 1000) {
                                opId = 222;
                                param /= 1000;
                            }
                            msg.writer().writeByte(opId);
                            msg.writer().writeShort(param);
                        } else {
                            msg.writer().writeByte(itemOption.optionTemplate.id);
                            msg.writer().writeShort(itemOption.param);
                        }
                    }
                }
            }
            player.sendMessage(msg);
            msg.cleanup();
        } catch (Exception e) {
        }
        Service.gI().Send_Caitrang(player);
    }

    public void sendItemBox(Player player) {
        Message msg;
        try {
            msg = new Message(-35);
            msg.writer().writeByte(0);
            msg.writer().writeByte(player.inventory.itemsBox.size());
            for (Item it : player.inventory.itemsBox) {
                msg.writer().writeShort(it.isNotNullItem() ? it.template.id : -1);
                if (it.isNotNullItem()) {
                    msg.writer().writeInt(it.quantity);
                    msg.writer().writeUTF(it.getInfo());
                    msg.writer().writeUTF(it.getContent());
                    List<Item.ItemOption> itemOptions = ItemService.gI().getVisibleItemOptions(it);
                    msg.writer().writeByte(itemOptions.size());
                    for (Item.ItemOption io : itemOptions) {
                        if (io.optionTemplate.id == 213) {
                            int opId = 213;
                            int param = io.param;
                            if (param > 1_000_000) {
                                opId = 223;
                                param /= 1_000_000;
                            } else if (param > 1000) {
                                opId = 222;
                                param /= 1000;
                            }
                            msg.writer().writeByte(opId);
                            msg.writer().writeShort(param);
                        } else {
                            msg.writer().writeByte(io.optionTemplate.id);
                            msg.writer().writeShort(io.param);
                        }
                    }
                }
            }
            player.sendMessage(msg);
            msg.cleanup();
        } catch (Exception e) {
        }
        this.openBox(player);
    }

    public void openBox(Player player) {
        Message msg;
        try {
            msg = new Message(-35);
            msg.writer().writeByte(1);
            player.sendMessage(msg);
            msg.cleanup();
        } catch (Exception e) {
        }
    }

    private void __________________Thêm_vật_phẩm_vào_danh_sách______________() {
        //**********************************************************************
    }

    private boolean addItemSpecial(Player player, Item item) {
        //bùa
        if (item.template.type == 13) {
            int min = 0;
            try {
                String tagShopBua = player.idMark.getShopOpen().tagName;
                if (tagShopBua.equals("BUA_1H")) {
                    min = 60;
                } else if (tagShopBua.equals("BUA_8H")) {
                    min = 60 * 8;
                } else if (tagShopBua.equals("BUA_1M")) {
                    min = 60 * 24 * 30;
                }
            } catch (Exception e) {
            }
            player.charms.addTimeCharms(item.template.id, min);
            return true;
        }

        switch (item.template.id) {
            case 453: //tàu tennis
                player.haveTennisSpaceShip = true;
                return true;
            case 74: //đùi gà nướng
                player.nPoint.setFullHpMp();
                PlayerService.gI().sendInfoHpMp(player);
                return true;
        }
        return false;
    }

    public boolean addItemBag(Player player, Item item) {
        //ngọc rồng đen
        if (ItemMapService.gI().isBlackBall(item.template.id)) {
            return BlackBallWarService.gI().pickBlackBall(player, item);
        }

        //ngọc rồng namek
        if (ItemMapService.gI().isNamecBall(item.template.id) || ItemMapService.gI().isNamecBallStone(item.template.id)) {
            return NgocRongNamecService.gI().pickNamekBall(player, item);
        }
        if (addItemSpecial(player, item)) {
            return true;
        }

        //gold, gem, ruby
        switch (item.template.type) {
            case 9:
                if (player.inventory.gold + item.quantity <= PlayerConfig.getMaxGold()) {
                    if (player.effectSkill.isChibi && player.typeChibi == 0) {
                        player.inventory.gold += item.quantity;
                    }
                    player.inventory.gold += item.quantity;
                    Service.gI().sendMoney(player);
                    return true;
                } else {
                    Service.gI().sendThongBao(player, "Vàng sau khi nhặt quá giới hạn cho phép");
                    return false;
                }
            case 10:
                long gem = (long) player.inventory.gem + (long) item.quantity;
                if (gem > Integer.MAX_VALUE) {
                    gem = Integer.MAX_VALUE;
                }
                player.inventory.gem = (int) gem;
                Service.gI().sendMoney(player);
                return true;
            case 34:
                long ruby = (long) player.inventory.ruby + (long) item.quantity;
                if (ruby > Integer.MAX_VALUE) {
                    ruby = Integer.MAX_VALUE;
                }
                player.inventory.ruby = (int) ruby;
                Service.gI().sendMoney(player);
                return true;
        }

        //mở rộng hành trang - rương đồ
        if (item.template.id == 517) {
            if (player.inventory.itemsBag.size() < PlayerConfig.getMaxBagSlots()) {
                player.inventory.itemsBag.add(ItemService.gI().createItemNull());
                Service.gI().sendThongBaoOK(player, "Hành trang của bạn đã được mở rộng thêm 1 ô");
                return true;
            } else {
                Service.gI().sendThongBaoOK(player, "Hành trang của bạn đã đạt tối đa");
                return false;
            }
        } else if (item.template.id == 518) {
            if (player.inventory.itemsBox.size() < PlayerConfig.getMaxBoxSlots()) {
                player.inventory.itemsBox.add(ItemService.gI().createItemNull());
                Service.gI().sendThongBaoOK(player, "Rương đồ của bạn đã được mở rộng thêm 1 ô");
                return true;
            } else {
                Service.gI().sendThongBaoOK(player, "Rương đồ của bạn đã đạt tối đa");
                return false;
            }
        }
        // Check item rồng nhí vĩnh viễn
        if (item.template.id >= 1765 && item.template.id <= 1771) {
            boolean check_options = false;
            for (Item.ItemOption op : item.itemOptions) {
                if (op.optionTemplate != null && op.optionTemplate.id == 93) {
                    check_options = true;
                    break;
                }
            }
            if (!check_options) {
                BadgesTaskService.updateCountBagesTask(player, ConstTaskBadges.ME_RONG, 1);
            }
        }
        boolean added = addItemList(player.inventory.itemsBag, item);
        if (added) {
            CostumeCollectionService.gI().recordOwnership(player, item);
        }
        return added;
    }

    public boolean addItemBox(Player player, Item item) {
        boolean added = addItemList(player.inventory.itemsBox, item);
        if (added) {
            CostumeCollectionService.gI().recordOwnership(player, item);
        }
        return added;
    }

    public boolean addItemList(List<Item> items, Item itemAdd) {
        ItemService.gI().resolveOption231(itemAdd);
        if (itemAdd.itemOptions.isEmpty()) {
            itemAdd.itemOptions.add(new Item.ItemOption(73, 0));
        }

        if (hasQuantityOption(itemAdd)) {
            syncQuantityOption(itemAdd);
            for (Item it : items) {
                if (it.isNotNullItem() && it.template.id == itemAdd.template.id
                        && hasQuantityOption(it)
                        && hasSameOptionsIgnoringQuantity(it.itemOptions, itemAdd.itemOptions)) {
                    int amountToStack = Math.min(100_000_000 - it.quantity, itemAdd.quantity);
                    if (amountToStack <= 0) {
                        continue;
                    }
                    it.quantity += amountToStack;
                    syncQuantityOption(it);
                    itemAdd.quantity -= amountToStack;
                    if (itemAdd.quantity == 0) {
                        return true;
                    }
                }
            }
        }

        int[] idParam = isItemIncrementalOption(itemAdd);
        if (idParam[0] != -1) {
            for (Item it : items) {
                if (it.isNotNullItem() && it.template.id == itemAdd.template.id) {
                    for (Item.ItemOption io : it.itemOptions) {
                        if (io.optionTemplate.id == idParam[0]) {
                            io.param += idParam[1];
                        }
                    }
                    itemAdd.quantity = 0;
                    return true;
                }
            }
        }
        if (itemAdd.template.isUpToUp) {
            for (Item it : items) {
                if (!it.isNotNullItem() || it.template.id != itemAdd.template.id || (!checkListsEqual(it.itemOptions, itemAdd.itemOptions) && itemAdd.template.id != 2074 && !itemAdd.isDaNangCap() && !itemAdd.isManhTS()) || it.quantity >= 100_000_000) {
                    continue;
                }

                //========================ITEM TĂNG SỐ LƯỢNG========================
                if ((itemAdd.template.id >= 1066 && itemAdd.template.id <= 1070) || itemAdd.template.id == 457
                        || itemAdd.template.id == 610 || itemAdd.template.type == 14 || itemAdd.template.id == 2048
                        || itemAdd.template.id > 2049 && itemAdd.template.id < 2056 || itemAdd.template.id == 821
                        || itemAdd.template.id == 2075) {
                    it.quantity += itemAdd.quantity;
                    itemAdd.quantity = 0;
                    return true;
                }

                if (it.quantity < 99999) {
                    int add = 99999 - it.quantity;
                    if (itemAdd.quantity <= add) {
                        it.quantity += itemAdd.quantity;
                        itemAdd.quantity = 0;
                        return true;
                    } else {
                        it.quantity = 99999;
                        itemAdd.quantity -= add;
                    }
                }
            }
        }

        //add item vào ô mới
        if (itemAdd.quantity > 0) {
            for (int i = 0; i < items.size(); i++) {
                if (!items.get(i).isNotNullItem()) {
                    items.set(i, ItemService.gI().copyItem(itemAdd));
                    itemAdd.quantity = 0;
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean checkListsEqual(List<ItemOption> list1, List<ItemOption> list2) {
        if (list1.size() != list2.size()) {
            return false;
        }

        for (int i = 0; i < list1.size(); i++) {
            if (list1.get(i).optionTemplate.id != list2.get(i).optionTemplate.id || list1.get(i).param != list2.get(i).param) {
                return false;
            }
        }

        return true;
    }

    private void __________________Kiểm_tra_điều_kiện_vật_phẩm______________() {
        //**********************************************************************
    }

    /**
     * Kiểm tra vật phẩm có phải là vật phẩm tăng chỉ số option hay không
     *
     * @param item
     * @return id option tăng chỉ số - param
     */
    private int[] isItemIncrementalOption(Item item) {
        for (Item.ItemOption io : item.itemOptions) {
            switch (io.optionTemplate.id) {
                case 1:
                    return new int[]{io.optionTemplate.id, io.param};
            }
        }
        return new int[]{-1, -1};
    }

    /** Keeps option 31 synchronized with the real stack quantity. */
    private void syncQuantityOption(Item item) {
        if (item == null || item.itemOptions == null) {
            return;
        }
        for (Item.ItemOption option : item.itemOptions) {
            if (option != null && option.optionTemplate != null && option.optionTemplate.id == 31) {
                option.param = Math.max(0, item.quantity);
                return;
            }
        }
    }

    private boolean hasQuantityOption(Item item) {
        if (item == null || item.itemOptions == null) {
            return false;
        }
        for (Item.ItemOption option : item.itemOptions) {
            if (option != null && option.optionTemplate != null && option.optionTemplate.id == 31) {
                return true;
            }
        }
        return false;
    }

    private boolean hasSameOptionsIgnoringQuantity(List<Item.ItemOption> first, List<Item.ItemOption> second) {
        List<Item.ItemOption> firstWithoutQuantity = new ArrayList<>();
        List<Item.ItemOption> secondWithoutQuantity = new ArrayList<>();
        for (Item.ItemOption option : first) {
            if (option.optionTemplate.id != 31) {
                firstWithoutQuantity.add(option);
            }
        }
        for (Item.ItemOption option : second) {
            if (option.optionTemplate.id != 31) {
                secondWithoutQuantity.add(option);
            }
        }
        return checkListsEqual(firstWithoutQuantity, secondWithoutQuantity);
    }

    private void __________________Kiểm_tra_danh_sách_còn_chỗ_trống_________() {
        //**********************************************************************
    }

    public byte getCountEmptyBag(Player player) {
        return getCountEmptyListItem(player.inventory.itemsBag);
    }

    public byte getCountEmptyListItem(List<Item> list) {
        byte count = 0;
        for (Item item : list) {
            if (!item.isNotNullItem()) {
                count++;
            }
        }
        return count;
    }

    public byte getIndexBag(Player pl, Item it) {
        for (byte i = 0; i < pl.inventory.itemsBag.size(); ++i) {
            Item item = pl.inventory.itemsBag.get(i);
            if (item != null && it.equals(item)) {
                return i;
            }
        }
        return -1;
    }

    public boolean finditemWoodChest(Player player) {
        for (Item item : player.inventory.itemsBag) {
            if (item.isNotNullItem() && item.template.id == 570) {
                return false;
            }
        }
        for (Item item : player.inventory.itemsBox) {
            if (item.isNotNullItem() && item.template.id == 570) {
                return false;
            }
        }
        return true;
    }

    public int getParam(Player player, int idoption, int itemID) {
        for (Item it : player.inventory.itemsBag) {
            if (it != null && it.itemOptions != null && it.isNotNullItem() && it.template.id == itemID) {
                for (ItemOption iop : it.itemOptions) {
                    if (iop.optionTemplate.id == idoption) {
                        return iop.param;
                    }
                }
            }
        }
        return 0;
    }

    public void subParamItemsBag(Player player, int itemID, int idoption, int param) {
        Item itemRemove = null;
        for (Item it : player.inventory.itemsBag) {
            if (it != null && it.template.id == itemID) {
                for (ItemOption op : it.itemOptions) {
                    if (op != null && op.optionTemplate.id == idoption) {
                        op.param -= param;
                        if (op.param <= 0) {
                            itemRemove = it;
                        }
                        break;
                    }
                }
                break;
            }
        }
        if (itemRemove != null) {
            removeItem(player.inventory.itemsBag, itemRemove);
        }
    }

    public boolean findItemBongTaiCap2(Player player) {
        for (Item item : player.inventory.itemsBag) {
            if (item.isNotNullItem() && item.template.id == 921) {
                return true;
            }
        }
        for (Item item : player.inventory.itemsBox) {
            if (item.isNotNullItem() && item.template.id == 921) {
                return true;
            }
        }
        return false;
    }
    public boolean findItemBongTaiCap3(Player player) {
        for (Item item : player.inventory.itemsBag) {
            if (item.isNotNullItem() && item.template.id == 1819) {
                return true;
            }
        }
        for (Item item : player.inventory.itemsBox) {
            if (item.isNotNullItem() && item.template.id == 1819) {
                return true;
            }
        }
        return false;
    }

    public boolean findItemBongTai(Player player) {
        for (Item item : player.inventory.itemsBag) {
            if (item.isNotNullItem() && (item.template.id == 921 || item.template.id == 454)) {
                return true;
            }
        }
        for (Item item : player.inventory.itemsBox) {
            if (item.isNotNullItem() && (item.template.id == 921 || item.template.id == 454)) {
                return true;
            }
        }
        return false;
    }

    public boolean findItemSkinQuyLaoKame(Player player) {
        for (Item item : player.inventory.itemsBody) {
            if (item.isNotNullItem() && item.template.id == 710) {
                return true;
            }
        }
        for (Item item : player.inventory.itemsBag) {
            if (item.isNotNullItem() && item.template.id == 710) {
                return true;
            }
        }
        for (Item item : player.inventory.itemsBox) {
            if (item.isNotNullItem() && item.template.id == 710) {
                return true;
            }
        }
        return false;
    }

    public boolean findItemNTK(Player player) {
        if (player.isPl()) {
            for (Item item : player.inventory.itemsBag) {
                if (item.isNotNullItem() && item.template.id == 992) {
                    return true;
                }
            }
            for (Item item : player.inventory.itemsBox) {
                if (item.isNotNullItem() && item.template.id == 992) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean findItemTVC(Player player) {
        if (player.isPl()) {
            for (Item item : player.inventory.itemsBag) {
                if (item.isNotNullItem() && item.template.id == 2077) {
                    return true;
                }
            }
            for (Item item : player.inventory.itemsBox) {
                if (item.isNotNullItem() && item.template.id == 2077) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean findItemTatVoGiangSinh(Player player) {
        for (Item item : player.inventory.itemsBag) {
            if (item.isNotNullItem() && item.template.id == 649) {
                return true;
            }
        }
        return false;
    }

    public boolean findSenzu(Player player) {
        for (Item item : player.inventory.itemsBag) {
            if (item.isNotNullItem() && item.template.type == 6) {
                return true;
            }
        }
        return false;
    }

    public boolean fullSetThan(Player player) {
        for (int i = 0; i < 5; i++) {
            Item item = player.inventory.itemsBody.get(i);
            if (item == null || item.template == null || item.template.level != 13) {
                return false;
            }
        }
        return true;
    }

    public boolean x99ThucAn(Player player) {
        Item doAn = player.inventory.itemsBag.stream().filter(it -> it != null && it.template != null && (it.template.id == 663 || it.template.id == 664 || it.template.id == 665 || it.template.id == 666 || it.template.id == 667) && it.quantity >= 99).findFirst().orElse(null);
        return doAn != null;
    }

    public boolean canOpenBillShop(Player player) {
        return fullSetThan(player) && x99ThucAn(player);
    }

    public boolean optionCanUpgrade(int id) {
        return id == 0 || id == 22 || id == 23 || id == 14 || id == 27 || id == 28 || id == 47;
    }

    public int getIndexItem(Player player, List<Item> items, Item item) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i) == item) {
                return i;
            }
        }
        return -1;
    }

    public int getIndexItemBag(Player player, Item item) {
        return getIndexItem(player, player.inventory.itemsBag, item);
    }

    public int getIndexItemBody(Player player, Item item) {
        return getIndexItem(player, player.inventory.itemsBody, item);
    }

    public int getIndexItemBox(Player player, Item item) {
        return getIndexItem(player, player.inventory.itemsBox, item);
    }

    private void handleOption210(Item item) {
        for (int i = 0; i < item.itemOptions.size(); i++) {
            Item.ItemOption io = item.itemOptions.get(i);
            if (io.optionTemplate.id == 210) {
                int option210Index = i;
                item.itemOptions.remove(i);
                int numberOfOptionsToAdd = io.param;
                int[] allOptions = {8, 14, 108, 94, 108, 16, 80, 81, 97, 100, 101, 104, 106};
                List<Integer> selectedOptions = new ArrayList<>();
                while (selectedOptions.size() < numberOfOptionsToAdd && selectedOptions.size() < allOptions.length) {
                    int randomIndex = (int) (Math.random() * allOptions.length);
                    int selectedOption = allOptions[randomIndex];
                    if (!selectedOptions.contains(selectedOption)) {
                        selectedOptions.add(selectedOption);
                    }
                }
                List<Item.ItemOption> newOptions = new ArrayList<>();
                for (int option : selectedOptions) {
                    int newParam = 0;
                    if (option == 8 || option == 14 || option == 108 || option == 94 || option == 108) {
                        newParam = 3 + (int) (Math.random() * 3);
                    } else if (option == 16 || option == 80 || option == 81 || option == 97 || option == 100 || option == 101 || option == 104) {
                        newParam = 10 + (int) (Math.random() * 16);
                    } else if (option == 106) {
                        newParam = 0;
                    }
                    newOptions.add(new Item.ItemOption(option, newParam));
                }
                item.itemOptions.addAll(option210Index, newOptions);
                break;
            }
        }
    }

    public boolean findItem(Player player, int id) {
        for (Item item : player.inventory.itemsBag) {
            if (item.isNotNullItem() && item.template.id == id) {
                return true;
            }
        }
        for (Item item : player.inventory.itemsBox) {
            if (item.isNotNullItem() && item.template.id == id) {
                return true;
            }
        }
        return false;
    }

    private void checkOption231(Item item) {
        ItemService.gI().resolveOption231(item);
    }
}
