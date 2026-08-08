package nro.models.combine;

import nro.models.consts.ConstNpc;
import nro.models.item.Item;
import nro.models.player.Player;
import nro.models.services.InventoryService;
import nro.models.services.Service;
import nro.models.utils.Util;

/**
 *
 * @author By Mr Blue
 */
public class PhaLeHoaTrangBi {

    public static void showInfoCombine(Player player) {
        if (player.combineNew.itemsCombine.size() != 1) {
            CombineService.gI().baHatMit.createOtherMenu(player, ConstNpc.IGNORE_MENU, "Hãy chọn 1 vật phẩm để pha lê hóa", "Đóng");
            return;
        }

        Item item = player.combineNew.itemsCombine.get(0);
        if (!CombineSystem.isTrangBiPhaLeHoa(item)) {
            CombineService.gI().baHatMit.createOtherMenu(player, ConstNpc.IGNORE_MENU, "Vật phẩm này không thể đục lỗ", "Đóng");
            return;
        }

        int star = 0;
        for (Item.ItemOption io : item.itemOptions) {
            if (io.optionTemplate.id == 107) {
                star = io.param;
            }
        }
        if (star >= CombineService.MAX_STAR_ITEM) {
            CombineService.gI().baHatMit.createOtherMenu(player, ConstNpc.IGNORE_MENU, "Vật phẩm đã đạt tối đa sao pha lê", "Đóng");
            return;
        }

        QuyTrinh(player, item, star);
    }

    private static void QuyTrinh(Player player, Item item, int star) {
        player.combineNew.goldCombine = CombineSystem.getGoldPhaLeHoa(star);
        player.combineNew.gemCombine = CombineSystem.getGemPhaLeHoa(star);
        player.combineNew.ratioCombine = getFakeRatio(star);

        String npcSay = item.template.name + "\n|2|";
        for (Item.ItemOption io : item.itemOptions) {
            if (io.optionTemplate.id != 102) {
                npcSay += io.getOptionString() + "\n";
            }
        }
        npcSay += "|7|Tỉ lệ thành công: " + player.combineNew.ratioCombine + "%" + "\n";

        if (player.combineNew.goldCombine <= player.inventory.gold) {
            npcSay += "|1|Cần " + Util.numberToMoney(player.combineNew.goldCombine) + " vàng";
            CombineService.gI().baHatMit.createOtherMenu(player, ConstNpc.MENU_START_COMBINE, npcSay,
                    "Nâng cấp\ncần " + player.combineNew.gemCombine + " ngọc", "Nâng cấp 10 lần", "Nâng cấp 100 lần");
        } else {
            npcSay += "Còn thiếu " + Util.numberToMoney(player.combineNew.goldCombine - player.inventory.gold) + " vàng";
            CombineService.gI().baHatMit.createOtherMenu(player, ConstNpc.IGNORE_MENU, npcSay, "Đóng");
        }
    }

    public static float getRatio(int star) {
        switch (star) {
            case 0:
                return 50f;
            case 1:
                return 20f;
            case 2:
                return 10f;
            case 3:
                return 5f;
            case 4:
                return 1f;
            case 5:
                return 0.7f;
            case 6:
                return 0.5f;
            case 7:
                return 0.1f;
            case 8:
                return 0.1f;
        }

        return 0;
    }

    private static int getFakeRatio(int star) {
        return switch (star) {
            case 0 ->
                80;
            case 1 ->
                40;
            case 2 ->
                30;
            case 3 ->
                20;
            case 4 ->
                10;
            case 5 ->
                5;
            case 6 ->
                3;
            case 7 ->
                2;
            case 8 ->
                1;
            default ->
                0;
        };
    }

    public static void phaLeHoa(Player player, int... numm) {
        if (player.idMark != null && !Util.canDoWithTime(player.idMark.getLastTimeCombine(), 500)) {
            return;
        }
        player.idMark.setLastTimeCombine(System.currentTimeMillis());
        int n = numm.length > 0 ? numm[0] : 1;

        if (!player.combineNew.itemsCombine.isEmpty()) {
            int gold = player.combineNew.goldCombine;
            int gem = player.combineNew.gemCombine;
            if (player.inventory.gold < gold) {
                Service.gI().sendThongBao(player, "Không đủ vàng để thực hiện");
                return;
            } else if (player.inventory.gem < gem) {
                Service.gI().sendThongBao(player, "Không đủ ngọc để thực hiện");
                return;
            }

            int num = 0;
            int star = 0;
            boolean success = false;
            int fail = 0;
            Item item = null;
            Item.ItemOption optionStar = null;

            for (int i = 0; i < n; i++) {
                num = i;
                gold = player.combineNew.goldCombine;
                gem = player.combineNew.gemCombine;
                if (player.inventory.gem < gem || player.inventory.gold < gold) {
                    break;
                }

                item = player.combineNew.itemsCombine.get(0);
                if (CombineSystem.isTrangBiPhaLeHoa(item)) {
                    star = 0;
                    optionStar = null;
                    for (Item.ItemOption io : item.itemOptions) {
                        if (io.optionTemplate.id == 107) {
                            star = io.param;
                            optionStar = io;
                            break;
                        }
                    }
                    if (star < CombineService.MAX_STAR_ITEM) {
                        player.combineNew.goldCombine = CombineSystem.getGoldPhaLeHoa(star);
                        player.combineNew.gemCombine = CombineSystem.getGemPhaLeHoa(star);

                        float baseRatio = getRatio(star);
                        player.inventory.gold -= gold;
                        player.inventory.gem -= gem;

                        boolean succ = Util.isTrue(baseRatio, 100);

                        if (succ) {
                            success = true;
                            break;
                        } else {
                            fail++;
                        }
                    }
                } else {
                    break;
                }
            }

            if (success) {
                star++;
                if (item != null) {
                    if (optionStar == null) {
                        item.itemOptions.add(new Item.ItemOption(107, star));
                    } else {
                        optionStar.param = star;
                    }
                    //  ChatGlobalService.gI().ThongBaoDapDo(player, "Chúc mừng " + player.name + " vừa pha lê hóa thành công " + item.template.name + " lên " + star + " sao pha lê");
                }
                CombineService.gI().sendEffectSuccessCombine(player);
                CombineService.gI().baHatMit.npcChat(player, "Chúc mừng con nhé");
            } else {
                CombineService.gI().sendEffectFailCombine(player);

                String[] failMessages = {
                    "Tay run à, đập kiểu gì thế?",
                    "Lại xịt rồi, hahaha...",
                    "Ngon bắt được con lợn béo rồi...!",
                    "Làm lại đi, biết đâu lần sau đỏ!",
                    "Lần sau nhớ khấn trước khi đập!",
                    "Kỹ năng quá kém?",
                    "Hên xui thôi mà, đừng cay!",
                    "Còn vàng còn ngọc, đập tiếp đi!"
                };
                String msg = failMessages[Util.nextInt(failMessages.length)];
                CombineService.gI().baHatMit.npcChat(player, msg);
            }

            InventoryService.gI().sendItemBags(player);
            Service.gI().sendMoney(player);
            CombineService.gI().reOpenItemCombine(player);
        }
    }
}
