package nro.models.services;

import nro.models.player.Currency;
import nro.models.player.WalletMutationContext;
import nro.models.player.WalletReason;
import nro.models.player.WalletResult;

import nro.models.managers.GiftCodeManager;
import nro.models.player_system.GiftCode;
import nro.models.item.Item;
import java.util.Set;
import nro.models.player.Player;
import nro.models.player.PlayerConfig;
import nro.models.map.service.NpcService;
import nro.models.shop.ItemShop;
import nro.models.shop.Shop;

public class GiftCodeService {

    private static GiftCodeService instance;

    public static GiftCodeService gI() {
        if (instance == null) {
            instance = new GiftCodeService();
        }
        return instance;
    }

    public void giftCode(Player player, String code) {
        GiftCode giftcode = GiftCodeManager.gI().findGiftCode(code);
        if (giftcode == null) {
            // int itemId = 190;
            // Item item = ItemService.gI().createNewItem(((short) itemId));
            // ItemShop it = new Shop().getItemShop(itemId);
            // if (it != null && !it.options.isEmpty()) {
            // item.itemOptions.addAll(it.options);
            // }
            // InventoryService.gI().addItemBag(player, item);
            // InventoryService.gI().sendItemBags(player);
            Service.gI().sendThongBao(player, "Code không chính xác!");
        } else if (!giftcode.hasStarted()) {
            Service.gI().sendThongBao(player, "Code chưa đến thời gian sử dụng");
        } else if (giftcode.hasExpired()) {
            Service.gI().sendThongBao(player, "Code đã hết hạn");
        } else {
            giftcode = GiftCodeManager.gI().checkUseGiftCode(player, code);
            if (giftcode == null) {
                return;
            }
            Set<Integer> keySet = giftcode.detail.keySet();
            String textGift = "|0|Bạn vừa nhận được:\b";
            for (Integer key : keySet) {
                int idItem = key;
                int quantity = giftcode.detail.get(key);

                switch (idItem) {
                    case -1 -> {
                        player.getWallet().creditUpToCap(Currency.GOLD, quantity, WalletMutationContext.of(WalletReason.ACTIVITY_REWARD, "GiftCode: vàng")).requireSuccess();
                        textGift += "|2|" + quantity + " vàng\b";
                    }
                    case -2 -> {
                        player.getWallet().creditUpToCap(Currency.GEM, quantity, WalletMutationContext.of(WalletReason.ACTIVITY_REWARD, "GiftCode: ngọc")).requireSuccess();
                        textGift += "|3|" + quantity + " ngọc\b";
                    }
                    case -3 -> {
                        player.getWallet().creditUpToCap(Currency.RUBY, quantity, WalletMutationContext.of(WalletReason.ACTIVITY_REWARD, "GiftCode: hồng ngọc")).requireSuccess();
                        textGift += "|4|" + quantity + " ngọc khóa\b";
                    }
                    default -> {
                        Item itemGiftTemplate = ItemService.gI().createNewItem((short) idItem);
                        if (itemGiftTemplate != null) {
                            Item itemGift = new Item((short) idItem);
                            boolean useDefaultOptions = Boolean.TRUE.equals(giftcode.useDefaultOptions.get(key));
                            itemGift.itemOptions = ItemService.gI().mergeItemOptions((short) idItem,
                                    useDefaultOptions, giftcode.option.get(key));
                            if (itemGift.template.id == 457 && itemGift.itemOptions.isEmpty()) {
                                itemGift.itemOptions.add(new Item.ItemOption(30, 0));
                            }
                            itemGift.quantity = quantity;
                            InventoryService.gI().addItemBag(player, itemGift);
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
