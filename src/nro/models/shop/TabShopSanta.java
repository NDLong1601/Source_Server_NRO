package nro.models.shop;

import nro.models.player.Player;
import java.util.ArrayList;
import java.util.List;
import nro.models.services.InventoryService;

/**
 *
 * @author By Mr Blue
 * 
 */

public class TabShopSanta extends TabShop {

    private final int[] listDauThan = {293, 294, 295, 296, 297, 298, 299, 596, 597, 598};

    public TabShopSanta(TabShop tabShop, Player player) {
        this.itemShops = new ArrayList<>();
        this.shop = tabShop.shop;
        this.id = tabShop.id;
        this.name = tabShop.name;

        int dauCanBuyId = idDauCanBuy(player);

        boolean hasBongTai = InventoryService.gI().findItemBongTai(player);

        for (ItemShop itemShop : tabShop.itemShops) {
            if (itemShop.temp.gender == player.gender || itemShop.temp.gender == 3) {
                boolean isInListDauThan = false;
                for (int id : listDauThan) {
                    if (itemShop.temp.id == id) {
                        isInListDauThan = true;
                        break;
                    }
                }
                if (!isInListDauThan || itemShop.temp.id == dauCanBuyId) {
                    this.itemShops.add(new ItemShop(itemShop));
                }
            }
        }
    }

    public int idDauCanBuy(Player player) {
        int level = player.magicTree.level;
        if (level == 10) {
            return listDauThan[9];
        } else if (level >= 1 && level <= 9) {
            return listDauThan[level];
        }
        throw new IllegalArgumentException("Invalid magic tree level: " + level);
    }
}
