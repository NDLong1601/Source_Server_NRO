package nro.models.combine;

import nro.models.consts.ConstNpc;
import nro.models.item.Item;
import nro.models.player.Player;
import nro.models.services.InventoryService;
import nro.models.services.ItemService;
import nro.models.services.Service;
import nro.models.player_system.Template;
import nro.models.utils.Util;

/**
 *
 * @author By Mr Blue
 */

public class TaoDaHematite {

    private static final int GOLD_TAO_DA = 50_000_000;  // Vàng cần thiết để tạo đá Hematite
    private static final int RATIO_TAO_DA = 100;  // Tỉ lệ thành công tạo đá Hematite (80%)

    public static void showInfoCombine(Player player) {
        if (player.combineNew.itemsCombine.size() == 1) {
            Item saoPhaLe = player.combineNew.itemsCombine.get(0);
            if (saoPhaLe.template.id >= 441 && saoPhaLe.template.id <= 447 && saoPhaLe.quantity >= 5) {
                // Nếu có đủ 5 sao pha lê trắng hoặc đen
                player.combineNew.goldCombine = GOLD_TAO_DA;
                player.combineNew.ratioCombine = RATIO_TAO_DA;

                String npcSay = "|2|Tạo đá Hematite từ sao pha lê\n";
                npcSay += "|2|Cần 5 sao pha lê Cấp 2\n";
                npcSay += "|2|Tỉ lệ thành công: " + player.combineNew.ratioCombine + "%\n";
                npcSay += "|2|Cần: " + Util.numberToMoney(player.combineNew.goldCombine) + " vàng\n";
                npcSay += "|7|Thất bại -5 sao pha lê\n";

                // Kiểm tra tài nguyên và đưa ra menu
                if (player.inventory.gold < player.combineNew.goldCombine) {
                    npcSay += "|7|Còn thiếu " + Util.powerToString(player.combineNew.goldCombine - player.inventory.gold) + " vàng\n";
                    CombineService.gI().baHatMit.createOtherMenu(player, ConstNpc.IGNORE_MENU, npcSay, "Đóng");
                } else {
                    CombineService.gI().baHatMit.createOtherMenu(player, ConstNpc.MENU_START_COMBINE, npcSay,
                            "Tạo đá Hematite\n" + Util.numberToMoney(player.combineNew.goldCombine) + " vàng\n", "Từ chối");
                }
            } else {
                CombineService.gI().baHatMit.createOtherMenu(player, ConstNpc.IGNORE_MENU,
                        "Cần 5 sao pha lê Cấp 2", "Đóng");
            }
        } else {
            CombineService.gI().baHatMit.createOtherMenu(player, ConstNpc.IGNORE_MENU,
                    "Cần 5 sao pha lê Cấp 2", "Đóng");
        }
    }

    public static void taoDaHematite(Player player) {
        if (player.combineNew.itemsCombine.size() == 1) {
            int gold = player.combineNew.goldCombine;

            if (player.inventory.gold < gold) {
                Service.gI().sendThongBao(player, "Không đủ vàng để thực hiện");
                return;
            }

            Item saoPhaLe = player.combineNew.itemsCombine.get(0);

            // Kiểm tra xem có đủ sao pha lê không
            if (saoPhaLe.template.id >= 441 && saoPhaLe.template.id <= 447 && saoPhaLe.quantity >= 5) {
                // Tiến hành trừ vàng và sao pha lê
                player.inventory.gold -= gold;
                InventoryService.gI().subQuantityItemsBag(player, saoPhaLe, 5);

                // Tạo đá Hematite
                if (Util.isTrue(player.combineNew.ratioCombine, 100)) {
                    Template.ItemTemplate hematiteTemplate = ItemService.gI().getTemplate(1423); // ID của đá Hematite
                    Item hematite = new Item();
                    hematite.template = hematiteTemplate;
                    hematite.quantity = 1;
                    InventoryService.gI().addItemBag(player, hematite);
                    CombineService.gI().sendEffectSuccessCombine(player);
                } else {
                    CombineService.gI().sendEffectFailCombine(player);
                }

                InventoryService.gI().sendItemBags(player);
                Service.gI().sendMoney(player);
                CombineService.gI().reOpenItemCombine(player);
            } else {
                Service.gI().sendThongBao(player, "Không đủ sao pha lê để tạo đá Hematite");
            }
        }
    }
}
