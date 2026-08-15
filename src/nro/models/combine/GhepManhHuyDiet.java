package nro.models.combine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import nro.models.consts.ConstNpc;
import nro.models.item.Item;
import nro.models.player.Player;
import nro.models.services.InventoryService;
import nro.models.services.ItemService;
import nro.models.services.Service;
import nro.models.utils.Util;

/**
 * Ghép 4 mảnh Hủy Diệt thành 1 Rương trang bị Hủy Diệt.
 *
 * Áo:   2027–2030 → 2047 (Rương Áo Hủy Diệt)
 * Quần: 2031–2034 → 2048 (Rương Quần Hủy Diệt)
 * Giày: 2035–2038 → 2050 (Rương Giày Hủy Diệt)
 * Găng: 2039–2042 → 2049 (Rương Găng Hủy Diệt)
 * Nhẫn: 2043–2046 → 2051 (Rương Nhẫn Hủy Diệt)
 *
 * Chi phí: 100.000.000 Vàng + 100 Ngọc. Tỉ lệ thành công: 50%.
 * Thất bại: Mất ngẫu nhiên 2 mảnh (giữ lại 2 mảnh), mất vàng và ngọc.
 * Hiệu ứng: Hào quang xuất hiện icon vật phẩm (giống ghép Ngọc Rồng).
 */
public class GhepManhHuyDiet {

    private static final int GOLD_COST = 100_000_000;
    private static final int GEM_COST = 100;
    private static final float RATIO_SUCCESS = 50.0f;

    // Mảng 5 bộ: mỗi bộ gồm [mảnh1, mảnh2, mảnh3, mảnh4, rương kết quả]
    private static final int[][] RECIPES = {
        {2027, 2028, 2029, 2030, 2047}, // Áo
        {2031, 2032, 2033, 2034, 2048}, // Quần
        {2035, 2036, 2037, 2038, 2050}, // Giày
        {2039, 2040, 2041, 2042, 2049}, // Găng
        {2043, 2044, 2045, 2046, 2051}, // Nhẫn
    };

    private static final String[] SET_NAMES = {"Áo", "Quần", "Giày", "Găng", "Nhẫn"};

    /**
     * Kiểm tra 1 item có phải là mảnh Hủy Diệt không (ID 2027–2046)
     */
    private static boolean isManhHuyDiet(Item item) {
        return item != null && item.isNotNullItem()
                && item.template.id >= 2027 && item.template.id <= 2046;
    }

    /**
     * Tìm bộ ghép (recipe index) cho 1 mảnh dựa vào template ID
     * @return index trong RECIPES, hoặc -1 nếu không tìm thấy
     */
    private static int findRecipeIndex(int templateId) {
        for (int i = 0; i < RECIPES.length; i++) {
            for (int j = 0; j < 4; j++) {
                if (RECIPES[i][j] == templateId) {
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * Hiển thị thông tin ghép khi player kéo thả nguyên liệu vào tab combine.
     */
    public static void showInfoCombine(Player player) {
        if (InventoryService.gI().getCountEmptyBag(player) <= 0) {
            CombineService.gI().baHatMit.createOtherMenu(player, ConstNpc.IGNORE_MENU,
                    "Hành trang cần ít nhất 1 chỗ trống", "Đóng");
            return;
        }

        if (player.combineNew.itemsCombine.size() != 4) {
            CombineService.gI().baHatMit.createOtherMenu(player, ConstNpc.IGNORE_MENU,
                    "Cần đặt đúng 4 mảnh Hủy Diệt cùng loại\n(Áo, Quần, Giày, Găng hoặc Nhẫn)", "Đóng");
            return;
        }

        // Kiểm tra tất cả 4 items phải là mảnh HD
        for (Item item : player.combineNew.itemsCombine) {
            if (!isManhHuyDiet(item)) {
                CombineService.gI().baHatMit.createOtherMenu(player, ConstNpc.IGNORE_MENU,
                        "Chỉ được đặt các mảnh Hủy Diệt", "Đóng");
                return;
            }
        }

        // Kiểm tra 4 mảnh phải cùng 1 bộ
        int recipeIdx = findRecipeIndex(player.combineNew.itemsCombine.get(0).template.id);
        if (recipeIdx < 0) {
            CombineService.gI().baHatMit.createOtherMenu(player, ConstNpc.IGNORE_MENU,
                    "Mảnh không hợp lệ", "Đóng");
            return;
        }

        // Kiểm tra có đủ 4 mảnh khác nhau của cùng bộ
        boolean[] found = new boolean[4];
        for (Item item : player.combineNew.itemsCombine) {
            boolean matched = false;
            for (int j = 0; j < 4; j++) {
                if (item.template.id == RECIPES[recipeIdx][j] && !found[j]) {
                    found[j] = true;
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                CombineService.gI().baHatMit.createOtherMenu(player, ConstNpc.IGNORE_MENU,
                        "Cần 4 mảnh khác nhau cùng bộ " + SET_NAMES[recipeIdx] + " Hủy Diệt\n"
                        + "(Mảnh 1, Mảnh 2, Mảnh 3, Mảnh 4)", "Đóng");
                return;
            }
        }

        for (boolean f : found) {
            if (!f) {
                CombineService.gI().baHatMit.createOtherMenu(player, ConstNpc.IGNORE_MENU,
                        "Cần đủ 4 mảnh khác nhau cùng bộ " + SET_NAMES[recipeIdx] + " Hủy Diệt", "Đóng");
                return;
            }
        }

        player.combineNew.goldCombine = GOLD_COST;
        player.combineNew.gemCombine = GEM_COST;
        player.combineNew.ratioCombine = RATIO_SUCCESS;

        // Hiển thị thông tin ghép + chi phí
        String resultName = ItemService.gI().getTemplate((short) RECIPES[recipeIdx][4]).name;
        String npcSay = "|2|Ghép 4 mảnh " + SET_NAMES[recipeIdx] + " Hủy Diệt thành\n"
                + resultName + "\n"
                + "|2|Tỉ lệ thành công: " + ((int) RATIO_SUCCESS) + "%\n"
                + "|2|Cần: 100 Tr vàng\n"
                + "|2|Cần: 100 ngọc\n"
                + "|7|Thất bại sẽ mất 2 mảnh ngẫu nhiên, vàng và ngọc (giữ lại 2 mảnh)";

        if (player.inventory.gold < GOLD_COST) {
            npcSay += "\n|7|Còn thiếu " + Util.powerToString(GOLD_COST - player.inventory.gold) + " vàng";
            CombineService.gI().baHatMit.createOtherMenu(player, ConstNpc.IGNORE_MENU, npcSay, "Đóng");
        } else if (player.inventory.gem < GEM_COST) {
            npcSay += "\n|7|Còn thiếu " + (GEM_COST - player.inventory.gem) + " ngọc";
            CombineService.gI().baHatMit.createOtherMenu(player, ConstNpc.IGNORE_MENU, npcSay, "Đóng");
        } else {
            CombineService.gI().baHatMit.createOtherMenu(player, ConstNpc.MENU_START_COMBINE, npcSay,
                    "Ghép mảnh\n100 Tr vàng\n100 ngọc", "Từ chối");
        }
    }

    /**
     * Thực hiện ghép 4 mảnh thành 1 rương.
     */
    public static void ghepManh(Player player) {
        if (InventoryService.gI().getCountEmptyBag(player) <= 0) {
            Service.gI().sendThongBao(player, "Hành trang cần ít nhất 1 chỗ trống");
            return;
        }
        if (player.combineNew.itemsCombine.size() != 4) {
            return;
        }

        // Kiểm tra vàng và ngọc
        if (player.inventory.gold < GOLD_COST) {
            Service.gI().sendThongBao(player, "Không đủ vàng để thực hiện");
            return;
        }
        if (player.inventory.gem < GEM_COST) {
            Service.gI().sendThongBao(player, "Không đủ ngọc để thực hiện");
            return;
        }

        // Tìm recipe
        int recipeIdx = findRecipeIndex(player.combineNew.itemsCombine.get(0).template.id);
        if (recipeIdx < 0) {
            return;
        }

        // Verify đủ 4 mảnh khác nhau
        boolean[] found = new boolean[4];
        for (Item item : player.combineNew.itemsCombine) {
            for (int j = 0; j < 4; j++) {
                if (item.template.id == RECIPES[recipeIdx][j] && !found[j]) {
                    found[j] = true;
                    break;
                }
            }
        }
        for (boolean f : found) {
            if (!f) {
                return;
            }
        }

        // Trừ chi phí: 100 triệu vàng + 100 ngọc
        player.inventory.subGold(GOLD_COST);
        player.inventory.subGem(GEM_COST);

        // Tỉ lệ thành công 50%
        if (Util.isTrue(50, 100)) {
            // Thành công: trừ toàn bộ 4 mảnh
            for (Item item : player.combineNew.itemsCombine) {
                InventoryService.gI().subQuantityItemsBag(player, item, 1);
            }

            // Tạo rương kết quả
            short resultId = (short) RECIPES[recipeIdx][4];
            Item ruong = ItemService.gI().createNewItem(resultId);
            InventoryService.gI().addItemBag(player, ruong);

            // Gửi hiệu ứng hào quang xuất hiện icon vật phẩm (giống ghép Ngọc Rồng)
            CombineService.gI().sendEffectCombineDB(player, (short) ruong.template.iconID);
            Service.gI().sendThongBao(player, "Ghép mảnh thành công! Bạn nhận được " + ruong.template.name);
        } else {
            // Thất bại: chỉ mất 2 mảnh ngẫu nhiên (giữ lại 2 mảnh)
            List<Integer> indices = new ArrayList<>(List.of(0, 1, 2, 3));
            Collections.shuffle(indices);
            int lostIdx1 = indices.get(0);
            int lostIdx2 = indices.get(1);

            Item lostItem1 = player.combineNew.itemsCombine.get(lostIdx1);
            Item lostItem2 = player.combineNew.itemsCombine.get(lostIdx2);
            InventoryService.gI().subQuantityItemsBag(player, lostItem1, 1);
            InventoryService.gI().subQuantityItemsBag(player, lostItem2, 1);

            // Gửi hiệu ứng thất bại
            CombineService.gI().sendEffectFailCombine(player);
            Service.gI().sendThongBao(player, "Ghép mảnh thất bại! Bạn bị mất 2 mảnh ("
                    + lostItem1.template.name + ", " + lostItem2.template.name + ") và giữ lại được 2 mảnh.");
        }

        Service.gI().sendMoney(player);
        InventoryService.gI().sendItemBags(player);
        CombineService.gI().reOpenItemCombine(player);
    }
}
