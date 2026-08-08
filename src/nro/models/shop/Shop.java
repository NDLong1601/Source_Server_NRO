package nro.models.shop;

import java.util.ArrayList;
import java.util.List;
import nro.models.player.Player;
import nro.models.shop.TabShopHocKynang;
import nro.models.shop.TabShopSoHuu;

/**
 *
 * @author By Mr Blue
 * 
 */

public class Shop {

    public int id;

    public byte npcId;

    public List<TabShop> tabShops;

    public String tagName;

    public byte typeShop;

    public Shop() {
        this.tabShops = new ArrayList<>();
    }

    public Shop(Shop shop, Player player) {
        this.tabShops = new ArrayList<>();
        this.id = shop.id;
        this.npcId = shop.npcId;
        this.tagName = shop.tagName;
        this.typeShop = shop.typeShop;
        for (TabShop tabShop : shop.tabShops) {
            if (tabShop.id >= 10 && tabShop.id <= 12) {
                this.tabShops.add(new TabShopUron(tabShop, player));
            } else if (tabShop.id == 17) {
                this.tabShops.add(new TabShopSanta(tabShop, player));
            } else if (tabShop.id == 19) {
                    this.tabShops.add(new TabShopMuaAvatar(tabShop, player));
            } else if (tabShop.id == 13) {
               
                this.tabShops.add(new TabShopHangDoc(tabShop, player));
            } else if (tabShop.id == 44) {
                this.tabShops.add(new TabShopDanhHieu(tabShop, player));
            } else if (tabShop.id == 45) {
                this.tabShops.add(new TabShopSoHuu(tabShop, player));
            } else if (tabShop.id >= 41 && tabShop.id <= 43) {
                this.tabShops.add(new TabShopHocKynang(tabShop, player));
            } else {
                this.tabShops.add(new TabShop(tabShop));
            }
        }
    }

    public Shop(Shop shop) {
        this.tabShops = new ArrayList<>();
        this.id = shop.id;
        this.npcId = shop.npcId;
        this.tagName = shop.tagName;
        this.typeShop = shop.typeShop;
        for (TabShop tabShop : shop.tabShops) {
            this.tabShops.add(new TabShop(tabShop));
        }
    }

    public ItemShop getItemShop(int temp) {
        for (TabShop tab : this.tabShops) {
            for (ItemShop is : tab.itemShops) {
                if (is.temp.id == temp) {
                    return is;
                }
            }
        }
        return null;
    }

    public void dispose() {
        if (this.tabShops != null) {
            for (TabShop ts : this.tabShops) {
                ts.dispose();
            }
            this.tabShops.clear();
        }
        this.tabShops = null;
    }

}
