package nro.models.services;

import nro.models.managers.GiftCodeManager;
import nro.models.player_system.GiftCode;
import nro.models.item.Item;
import java.util.Set;
import nro.models.player.Player;
import nro.models.map.service.NpcService;
import nro.models.shop.ItemShop;
import nro.models.shop.Shop;

/**
 *
 * @author By Mr Blue
 * 
 */

public class GiftCodeService {

    private static GiftCodeService instance;

    public static GiftCodeService gI() {
        if (instance == null) {
            instance = new GiftCodeService();
        }
        return instance;
    }

    public void giftCode(Player player, String code) {
        GiftCode giftcode = GiftCodeManager.gI().checkUseGiftCode(player, code);
        if (giftcode == null) {
//            int itemId = 190;
//            Item item = ItemService.gI().createNewItem(((short) itemId));
//            ItemShop it = new Shop().getItemShop(itemId);
//            if (it != null && !it.options.isEmpty()) {
//                item.itemOptions.addAll(it.options);
//            }
//            InventoryService.gI().addItemBag(player, item);
//            InventoryService.gI().sendItemBags(player);
            Service.gI().sendThongBao(player, "Code không chính xác!");
        } else if (giftcode.timeCode()) {
            Service.gI().sendThongBao(player, "Code đã hết hạn");
        } else {
            Set<Integer> keySet = giftcode.detail.keySet();
            String textGift = "|0|Bạn vừa nhận được:\b";
            for (Integer key : keySet) {
                int idItem = key;
                int quantity = giftcode.detail.get(key);

                switch (idItem) {
                    case -1 -> {
                        player.inventory.gold = Math.min(player.inventory.gold + (long) quantity, 2000000000L);
                        textGift += "|2|" + quantity + " vàng\b";
                    }
                    case -2 -> {
                        player.inventory.gem = Math.min(player.inventory.gem + quantity, 200000000);
                        textGift += "|3|" + quantity + " ngọc\b";
                    }
                    case -3 -> {
                        player.inventory.ruby = Math.min(player.inventory.ruby + quantity, 200000000);
                        textGift += "|4|" + quantity + " ngọc khóa\b";
                    }
                    default -> {
                        Item itemGiftTemplate = ItemService.gI().createNewItem((short) idItem);
                        if (itemGiftTemplate != null) {
                            Item itemGift = new Item((short) idItem);

                            if (itemGift.template.type == 0 || itemGift.template.type == 1 || itemGift.template.type == 2 || itemGift.template.type == 3
                                    || itemGift.template.type == 4 || itemGift.template.type == 5) {
                                if (itemGift.template.id == 457) {
                                    itemGift.itemOptions.add(new Item.ItemOption(30, 0));
                                } else {
                                    itemGift.itemOptions = giftcode.option.get(key);
                                    itemGift.quantity = quantity;
                                    InventoryService.gI().addItemBag(player, itemGift);
                                }
                            } else {
                                itemGift.itemOptions = giftcode.option.get(key);
                                itemGift.quantity = quantity;
                                InventoryService.gI().addItemBag(player, itemGift);
                            }
                            textGift += "|1|x" + quantity + " " + itemGift.template.name + "\b";
                        }
                    }
                }
            }
            InventoryService.gI().sendItemBags(player);
            NpcService.gI().createTutorial(player, 1139, textGift);
        }
    }

}
