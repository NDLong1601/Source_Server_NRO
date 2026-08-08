package nro.models.combine;

import nro.models.consts.ConstNpc;
import nro.models.item.Item;
import nro.models.player.Player;
import nro.models.services.Service;
import nro.models.services.InventoryService;
import nro.models.utils.Util;

/**
 *
 * @author By Mr Blue
 */

public class HoiPhucSach {

    private static int getGem(int param) {
        int gem = (1000 - param) * 50 / 1000;
        if (gem < 5) {
            gem = 5;
        }
        return gem;
    }
    
    public static void showInfoCombine(Player player) {
        if (player.combineNew.itemsCombine.size() != 1) {
            Service.gI().sendDialogMessage(player, "Cần Sách Tuyệt Kỹ hỏng để phục hồi.");
            return;
        }
        Item sachTuyetKy = null;
        for (Item item : player.combineNew.itemsCombine) {
            if (item.isSachTuyetKy() || item.isSachTuyetKy2()) {
                sachTuyetKy = item;
            }
        }
        if (sachTuyetKy == null || sachTuyetKy.getOptionParam(212) >= 1000) {
            Service.gI().sendDialogMessage(player, "Cần Sách Tuyệt Kỹ hỏng để phục hồi.");
            return;
        }
        int doBen = sachTuyetKy.getOptionParam(212);
        StringBuilder text = new StringBuilder();
        text.append("Phục hồi Sách Tuyệt Kỹ ?\n");
        text.append("1 cuốn\n");
        text.append(1000 - doBen).append(" điểm độ bền cần hồi phục\n");
        text.append("Cần ").append(getGem(doBen)).append(" ngọc");
        if (player.inventory.getGem() < getGem(doBen)) {
            CombineService.gI().baHatMit.createOtherMenu(player, ConstNpc.IGNORE_MENU, text.toString(),
                    "Còn thiếu\n" + Util.numberToMoney(getGem(doBen) - player.inventory.getGem()) + " ngọc");
            return;
        }
        CombineService.gI().baHatMit.createOtherMenu(player, ConstNpc.MENU_START_COMBINE, text.toString(),
                "Đồng ý", "Từ chối");
    }

    public static void hoiPhucSach(Player player) {
        if (player.combineNew.itemsCombine.size() != 1) {
            return;
        }
        Item sachTuyetKy = null;
        for (Item item : player.combineNew.itemsCombine) {
            if (item.isSachTuyetKy() || item.isSachTuyetKy2()) {
                sachTuyetKy = item;
            }
        }
        if (sachTuyetKy == null || sachTuyetKy.getOptionParam(212) >= 1000) {
            return;
        }
        int doBen = sachTuyetKy.getOptionParam(212);
        if (player.inventory.getGem() < getGem(doBen)) {
            return;
        }
        player.inventory.subGem(getGem(doBen));
        for (Item.ItemOption io : sachTuyetKy.itemOptions) {
            if (io.optionTemplate.id == 212) {
                io.param = 1000;
                break;
            }
        }
        CombineService.gI().sendEffectSuccessCombine(player);
        Service.gI().sendMoney(player);
        InventoryService.gI().sendItemBags(player);
        CombineService.gI().reOpenItemCombine(player);
    }
}
