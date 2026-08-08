package nro.models.services_func;

import nro.models.item.Item;
import nro.models.player.Player;
import nro.models.services.ItemService;
import nro.models.shop.ShopService;

/**
 *
 * @author By Mr Blue
 * 
 */

public class BuyBackService {

    private static final byte MAX_ITEM_IN_BOX = 10;

    private static BuyBackService i;

    public static BuyBackService gI() {
        if (i == null) {
            i = new BuyBackService();
        }
        return i;
    }

    public void addItem(Player player, Item item) {
        if (player.inventory.itemsDaBan.size() + 1 > MAX_ITEM_IN_BOX) {
            player.inventory.itemsDaBan.remove(0);
        }
        Item itemmua = ItemService.gI().copyItem(item);
        player.inventory.itemsDaBan.add(itemmua);
        if (player.idMark != null && player.idMark.getTagNameShop().equals("ITEMS_DABAN")) {
            ShopService.gI().opendShop(player, "ITEMS_DABAN", true);
        }
    }
}
