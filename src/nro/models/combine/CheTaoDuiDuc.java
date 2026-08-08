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

public class CheTaoDuiDuc {

    private static final int GOLD_TAO_DA = 50_000_000;  
    private static final int RATIO_TAO_DA = 100; 

    public static void showInfoCombine(Player player) {
        if (player.combineNew.itemsCombine.size() == 1) {
            Item Hematite = player.combineNew.itemsCombine.get(0);
            if (Hematite.template.id == 1423 && Hematite.quantity >= 5) {
                player.combineNew.goldCombine = GOLD_TAO_DA;
                player.combineNew.ratioCombine = RATIO_TAO_DA;

                String npcSay = "|2|Tạo Dùi Đục từ Đá Hematite\n";
                npcSay += "|2|Cần 5 viên Hematite\n";
                npcSay += "|2|Tỉ lệ thành công: " + player.combineNew.ratioCombine + "%\n";
                npcSay += "|2|Cần: " + Util.numberToMoney(player.combineNew.goldCombine) + " vàng\n";
                npcSay += "|7|Thất bại -5 đá Hematite\n";

                // Kiểm tra tài nguyên và đưa ra menu
                if (player.inventory.gold < player.combineNew.goldCombine) {
                    npcSay += "|7|Còn thiếu " + Util.powerToString(player.combineNew.goldCombine - player.inventory.gold) + " vàng\n";
                    CombineService.gI().baHatMit.createOtherMenu(player, ConstNpc.IGNORE_MENU, npcSay, "Đóng");
                } else {
                    CombineService.gI().baHatMit.createOtherMenu(player, ConstNpc.MENU_START_COMBINE, npcSay,
                            "Tạo đá Dùi Đục\n" + Util.numberToMoney(player.combineNew.goldCombine) + " vàng\n", "Từ chối");
                }
            } else {
                CombineService.gI().baHatMit.createOtherMenu(player, ConstNpc.IGNORE_MENU,
                        "Cần 5 Viên Đá Hematite", "Đóng");
            }
        } else {
            CombineService.gI().baHatMit.createOtherMenu(player, ConstNpc.IGNORE_MENU,
                    "Cần 5 Viên Đá Hematite", "Đóng");
        }
    }

    public static void CheTaoDuiDuc(Player player) {
        if (player.combineNew.itemsCombine.size() == 1) {
            int gold = player.combineNew.goldCombine;

            if (player.inventory.gold < gold) {
                Service.gI().sendThongBao(player, "Không đủ vàng để thực hiện");
                return;
            }

            Item Hematite = player.combineNew.itemsCombine.get(0);
            if (Hematite.template.id == 1423 && Hematite.quantity >= 5) {
                player.inventory.gold -= gold;
                InventoryService.gI().subQuantityItemsBag(player, Hematite, 5);
                if (Util.isTrue(player.combineNew.ratioCombine, 100)) {
                    Template.ItemTemplate hematiteTemplate = ItemService.gI().getTemplate(1438); 
                    Item DuiDuc = new Item();
                    DuiDuc.template = hematiteTemplate;
                    DuiDuc.quantity = 1;
                    InventoryService.gI().addItemBag(player, DuiDuc);
                    CombineService.gI().sendEffectSuccessCombine(player);
                } else {
                    CombineService.gI().sendEffectFailCombine(player);
                }

                InventoryService.gI().sendItemBags(player);
                Service.gI().sendMoney(player);
                CombineService.gI().reOpenItemCombine(player);
            } else {
                Service.gI().sendThongBao(player, "Không đủ đá Hematite để tạo Dùi Đục");
            }
        }
    }
}
