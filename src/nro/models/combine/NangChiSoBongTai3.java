package nro.models.combine;

import nro.models.consts.ConstNpc;
import nro.models.item.Item;
import nro.models.player.Player;
import nro.models.services.InventoryService;
import nro.models.services.Service;
import nro.models.utils.Util;

/**
 *
 * @author MinhDu
 */
public class NangChiSoBongTai3 {
    // Chi phí & tỉ lệ
    private static final int GEM_NANG_BT = 1_000;
    private static final int RATIO_NANG_CAP = 30;
    private static final int ITEM_PARAM_INDEX = 31;
    private static final int BONG_TAI_C3_ID = 1819;
    private static final int HON_BONG_TAI_ID = 934;
    private static final int DA_XANH_LAM_ID = 935;
    private static final int REQUIRED_HON_BONG_TAI = 99;
    private static final byte[] UPGRADE_OPTIONS = {77, 80, 81, 103, 50, 94, 5};
    private static final byte PARAM_MIN = 5;
    private static final byte PARAM_MAX = 15;

    public static void showInfoCombine(Player player) {
        if (player.combineNew.itemsCombine.size() == 3) {
            Item bongTai = null, honBongTai = null, daXanhLam = null;

            for (Item item : player.combineNew.itemsCombine) {
                if (item != null && item.isNotNullItem()) {
                    int id = item.template.id;
                    if (id == BONG_TAI_C3_ID) bongTai = item;
                    else if (id == HON_BONG_TAI_ID) honBongTai = item;
                    else if (id == DA_XANH_LAM_ID) daXanhLam = item;
                }
            }

            if (bongTai != null && honBongTai != null && daXanhLam != null) {
                player.combineNew.gemCombine = GEM_NANG_BT;
                player.combineNew.ratioCombine = RATIO_NANG_CAP;

                int currentHon = InventoryService.gI().getParam(player, ITEM_PARAM_INDEX, HON_BONG_TAI_ID);

                String npcSay = "|2|Mở chỉ số Bông tai Porata [+3]\n\n";
                npcSay += "|2|Tỉ lệ thành công: " + player.combineNew.ratioCombine + "%\n";

                if (daXanhLam.quantity < 1) {
                    npcSay += "|2|Cần " + REQUIRED_HON_BONG_TAI + " " + honBongTai.template.name + "\n";
                    npcSay += "|7|Cần 1 " + daXanhLam.template.name + "\n";
                    npcSay += "|2|Cần: " + player.combineNew.gemCombine + " ngọc\n";
                    npcSay += "|1|Kết quả: +2 dòng chỉ số ngẫu nhiên (có thể trùng nhau)\n";
                    npcSay += "|2|Còn thiếu " + (1 - daXanhLam.quantity) + " " + daXanhLam.template.name;
                    CombineService.gI().baHatMit.createOtherMenu(player, ConstNpc.IGNORE_MENU, npcSay, "Đóng");
                } else if (currentHon < REQUIRED_HON_BONG_TAI) {
                    npcSay += "|7|Cần " + REQUIRED_HON_BONG_TAI + " " + honBongTai.template.name + "\n";
                    npcSay += "|2|Cần 1 " + daXanhLam.template.name + "\n";
                    npcSay += "|2|Cần: " + player.combineNew.gemCombine + " ngọc\n";
                    npcSay += "|1|Kết quả: +2 dòng chỉ số ngẫu nhiên (có thể trùng nhau)\n";
                    npcSay += "|2|Còn thiếu " + (REQUIRED_HON_BONG_TAI - currentHon) + " " + honBongTai.template.name;
                    CombineService.gI().baHatMit.createOtherMenu(player, ConstNpc.IGNORE_MENU, npcSay, "Đóng");
                } else if (player.inventory.gem < player.combineNew.gemCombine) {
                    npcSay += "|2|Cần " + REQUIRED_HON_BONG_TAI + " " + honBongTai.template.name + "\n";
                    npcSay += "|2|Cần 1 " + daXanhLam.template.name + "\n";
                    npcSay += "|7|Cần: " + player.combineNew.gemCombine + " ngọc\n";
                    npcSay += "|1|Kết quả: +2 dòng chỉ số ngẫu nhiên (có thể trùng nhau)\n";
                    npcSay += "|2|Còn thiếu " + (player.combineNew.gemCombine - player.inventory.gem) + " ngọc";
                    CombineService.gI().baHatMit.createOtherMenu(player, ConstNpc.IGNORE_MENU, npcSay, "Đóng");
                } else {
                    npcSay += "|2|Cần " + REQUIRED_HON_BONG_TAI + " " + honBongTai.template.name + "\n";
                    npcSay += "|2|Cần 1 " + daXanhLam.template.name + "\n";
                    npcSay += "|2|Cần: " + player.combineNew.gemCombine + " ngọc\n";
                    npcSay += "|1|Kết quả: +2 dòng chỉ số ngẫu nhiên (có thể trùng nhau)";
                    CombineService.gI().baHatMit.createOtherMenu(
                            player,
                            ConstNpc.MENU_START_COMBINE,
                            npcSay,
                            "Nâng cấp\n" + player.combineNew.gemCombine + " ngọc",
                            "Từ chối"
                    );
                }
            } else {
                CombineService.gI().baHatMit.createOtherMenu(
                        player,
                        ConstNpc.IGNORE_MENU,
                        "Cần 1 Bông tai Porata cấp 3, x" + REQUIRED_HON_BONG_TAI + " Hồn bông tai và 1 Đá xanh lam",
                        "Đóng"
                );
            }
        } else {
            CombineService.gI().baHatMit.createOtherMenu(
                    player,
                    ConstNpc.IGNORE_MENU,
                    "Cần 1 Bông tai Porata cấp 3, x" + REQUIRED_HON_BONG_TAI + " Hồn bông tai và 1 Đá xanh lam",
                    "Đóng"
            );
        }
    }
    public static void nangChiSoBongTai(Player player) {
        try {
            int currentHon = InventoryService.gI().getParam(player, ITEM_PARAM_INDEX, HON_BONG_TAI_ID);
            Item daXanhLam = InventoryService.gI().findItemBag(player, DA_XANH_LAM_ID);

            if (currentHon < REQUIRED_HON_BONG_TAI || daXanhLam == null || daXanhLam.quantity < 1) {
                Service.gI().sendThongBao(player, "Không đủ vật phẩm để thực hiện.");
                return;
            }
            if (player.inventory.gem < player.combineNew.gemCombine) {
                Service.gI().sendThongBao(player, "Bạn không đủ ngọc, còn thiếu " + (player.combineNew.gemCombine - player.inventory.gem) + " ngọc nữa!");
                return;
            }
            player.inventory.gem -= player.combineNew.gemCombine;
            Item bongTai = player.combineNew.itemsCombine.stream()
                    .filter(it -> it != null && it.isNotNullItem() && it.template != null && it.template.id == BONG_TAI_C3_ID)
                    .findFirst()
                    .orElse(null);

            if (bongTai == null) {
                Service.gI().sendThongBao(player, "Thiếu Bông tai Porata cấp 3.");
                return;
            }

            boolean success = Util.isTrue(player.combineNew.ratioCombine, 100);
            if (success) {
                byte opt1 = randomOpt();
                byte opt2 = randomOpt();
                byte p1 = (byte) Util.nextInt(PARAM_MIN, PARAM_MAX);
                byte p2 = (byte) Util.nextInt(PARAM_MIN, PARAM_MAX);
                bongTai.itemOptions.clear();
                bongTai.itemOptions.add(new Item.ItemOption(opt1, p1));
                bongTai.itemOptions.add(new Item.ItemOption(opt2, p2));
                bongTai.itemOptions.add(new Item.ItemOption((short) 72, 3));

                CombineService.gI().sendEffectSuccessCombine(player);
            } else {
                CombineService.gI().sendEffectFailCombine(player);
            }
            InventoryService.gI().subParamItemsBag(player, HON_BONG_TAI_ID, ITEM_PARAM_INDEX, REQUIRED_HON_BONG_TAI);
            InventoryService.gI().subQuantityItemsBag(player, daXanhLam, 1);

            Service.gI().sendMoney(player);
            InventoryService.gI().sendItemBags(player);
            CombineService.gI().reOpenItemCombine(player);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static byte randomOpt() {
        return UPGRADE_OPTIONS[Util.nextInt(0, UPGRADE_OPTIONS.length - 1)];
    }
}
