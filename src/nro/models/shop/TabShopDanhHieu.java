package nro.models.shop;

import nro.models.item.Item;
import nro.models.player.Player;
import nro.models.player_badges.BagesTemplate;
import nro.models.shop.ItemShop;
import nro.models.shop.TabShop;
import nro.models.task.BadgesTaskService;
import java.util.ArrayList;
import java.util.List;
import nro.models.player_badges.BagesTemplate;

/**
 *
 * @author By Mr Blue
 * 
 */

public class TabShopDanhHieu extends TabShop {

    public TabShopDanhHieu(TabShop tabShop, Player player) {
        this.itemShops = new ArrayList<>();
        this.shop = tabShop.shop;
        this.id = tabShop.id;
        this.name = tabShop.name;

        for (ItemShop itemShop : tabShop.itemShops) {
            if (itemShop.temp.gender == player.gender || itemShop.temp.gender > 2) {
                boolean shouldAdd = true;
                for (Integer i : BagesTemplate.listEffect(player)) {
                    if (itemShop.temp.id == i) {
                        shouldAdd = false;
                        break;
                    }
                }

                if (shouldAdd) {
                    List<Item.ItemOption> listOptionBackup = new ArrayList<>();
                    for (Item.ItemOption opt : itemShop.options) {
                        if (opt.optionTemplate.id != 220) {
                            listOptionBackup.add(opt);
                        }
                    }

                    itemShop.options.clear();
                    int percent = BadgesTaskService.sendPercenBadgesTask(player, BagesTemplate.fineIdEffectbyIdItem(itemShop.temp.id));

                    itemShop.options.addAll(listOptionBackup);
                    itemShop.options.add(new Item.ItemOption(220, percent));

                    this.itemShops.add(new ItemShop(itemShop));
                }
            }
        }
    }
}
