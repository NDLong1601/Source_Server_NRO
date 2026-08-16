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
 * Chi phí, tỉ lệ, số mảnh mất khi thất bại và công thức đều có thể cấu hình
 * qua combine.properties / NRO Admin Data.
 * Hiệu ứng: Hào quang xuất hiện icon vật phẩm (giống ghép Ngọc Rồng).
 */
public class GhepManhHuyDiet {

    private static final int DEFAULT_GOLD_COST = 100_000_000;
    private static final int DEFAULT_GEM_COST = 100;
    private static final int DEFAULT_FAIL_LOST_FRAGMENTS = 2;
    private static final double DEFAULT_SUCCESS_RATE = 50D;

    // Mảng 5 bộ: mỗi bộ gồm [mảnh1, mảnh2, mảnh3, mảnh4, rương kết quả]
    private static final int[][] DEFAULT_RECIPES = {
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
    private static boolean isManhHuyDiet(Item item, int[][] recipes) {
        if (item == null || !item.isNotNullItem()) {
            return false;
        }
        return findRecipeIndex(item.template.id, recipes) >= 0;
    }

    /**
     * Tìm bộ ghép (recipe index) cho 1 mảnh dựa vào template ID
     * @return index trong RECIPES, hoặc -1 nếu không tìm thấy
     */
    private static int findRecipeIndex(int templateId, int[][] recipes) {
        for (int i = 0; i < recipes.length; i++) {
            for (int j = 0; j < 4; j++) {
                if (recipes[i][j] == templateId) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static int[][] getRecipes() {
        int[] defaults = new int[DEFAULT_RECIPES.length * 5];
        for (int i = 0; i < DEFAULT_RECIPES.length; i++) {
            System.arraycopy(DEFAULT_RECIPES[i], 0, defaults, i * 5, 5);
        }
        int[] configured = CombineConfig.getIntArray("destroy.merge.recipes", defaults);
        if (configured.length != defaults.length) {
            return copyDefaultRecipes();
        }
        int[][] recipes = new int[DEFAULT_RECIPES.length][5];
        for (int i = 0; i < recipes.length; i++) {
            System.arraycopy(configured, i * 5, recipes[i], 0, 5);
        }
        return isValidRecipes(recipes) ? recipes : copyDefaultRecipes();
    }

    private static int[][] copyDefaultRecipes() {
        int[][] copy = new int[DEFAULT_RECIPES.length][5];
        for (int i = 0; i < DEFAULT_RECIPES.length; i++) {
            System.arraycopy(DEFAULT_RECIPES[i], 0, copy[i], 0, 5);
        }
        return copy;
    }

    private static boolean isValidRecipes(int[][] recipes) {
        for (int i = 0; i < recipes.length; i++) {
            for (int j = 0; j < 4; j++) {
                if (recipes[i][j] <= 0 || recipes[i][j] > Short.MAX_VALUE) {
                    return false;
                }
                for (int other = j + 1; other < 4; other++) {
                    if (recipes[i][j] == recipes[i][other]) {
                        return false;
                    }
                }
                for (int previous = 0; previous < i; previous++) {
                    for (int old = 0; old < 4; old++) {
                        if (recipes[i][j] == recipes[previous][old]) {
                            return false;
                        }
                    }
                }
            }
            if (recipes[i][4] <= 0 || recipes[i][4] > Short.MAX_VALUE) {
                return false;
            }
            for (int j = 0; j < 4; j++) {
                if (recipes[i][4] == recipes[i][j]) {
                    return false;
                }
            }
        }
        return true;
    }

    private static int getGoldCost() {
        return CombineConfig.getInt("destroy.merge.goldCost", DEFAULT_GOLD_COST, 0, Integer.MAX_VALUE);
    }

    private static int getGemCost() {
        return CombineConfig.getInt("destroy.merge.gemCost", DEFAULT_GEM_COST, 0, Integer.MAX_VALUE);
    }

    private static int getFailLostFragments() {
        return CombineConfig.getInt("destroy.merge.failLostFragments", DEFAULT_FAIL_LOST_FRAGMENTS, 1, 4);
    }

    private static double getSuccessRate() {
        return CombineConfig.getRate("destroy.merge.successRate", DEFAULT_SUCCESS_RATE);
    }

    /**
     * Hiển thị thông tin ghép khi player kéo thả nguyên liệu vào tab combine.
     */
    public static void showInfoCombine(Player player) {
        int[][] recipes = getRecipes();
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
            if (!isManhHuyDiet(item, recipes)) {
                CombineService.gI().baHatMit.createOtherMenu(player, ConstNpc.IGNORE_MENU,
                        "Chỉ được đặt các mảnh Hủy Diệt", "Đóng");
                return;
            }
        }

        // Kiểm tra 4 mảnh phải cùng 1 bộ
        int recipeIdx = findRecipeIndex(player.combineNew.itemsCombine.get(0).template.id, recipes);
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
                if (item.template.id == recipes[recipeIdx][j] && !found[j]) {
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

        int goldCost = getGoldCost();
        int gemCost = getGemCost();
        int failLostFragments = getFailLostFragments();
        double successRate = getSuccessRate();
        player.combineNew.goldCombine = goldCost;
        player.combineNew.gemCombine = gemCost;
        player.combineNew.ratioCombine = (float) successRate;

        // Hiển thị thông tin ghép + chi phí
        String resultName = ItemService.gI().getTemplate((short) recipes[recipeIdx][4]).name;
        String npcSay = "|2|Ghép 4 mảnh " + SET_NAMES[recipeIdx] + " Hủy Diệt thành\n"
                + resultName + "\n"
                + "|2|Tỉ lệ thành công: " + successRate + "%\n"
                + "|2|Cần: " + Util.numberToMoney(goldCost) + " vàng\n"
                + "|2|Cần: " + gemCost + " ngọc\n"
                + "|7|Thất bại sẽ mất " + failLostFragments + " mảnh ngẫu nhiên, vàng và ngọc (giữ lại " + (4 - failLostFragments) + " mảnh)";

        if (player.inventory.gold < goldCost) {
            npcSay += "\n|7|Còn thiếu " + Util.powerToString(goldCost - player.inventory.gold) + " vàng";
            CombineService.gI().baHatMit.createOtherMenu(player, ConstNpc.IGNORE_MENU, npcSay, "Đóng");
        } else if (player.inventory.gem < gemCost) {
            npcSay += "\n|7|Còn thiếu " + (gemCost - player.inventory.gem) + " ngọc";
            CombineService.gI().baHatMit.createOtherMenu(player, ConstNpc.IGNORE_MENU, npcSay, "Đóng");
        } else {
            CombineService.gI().baHatMit.createOtherMenu(player, ConstNpc.MENU_START_COMBINE, npcSay,
                    "Ghép mảnh\n" + Util.numberToMoney(goldCost) + " vàng\n" + gemCost + " ngọc", "Từ chối");
        }
    }

    /**
     * Thực hiện ghép 4 mảnh thành 1 rương.
     */
    public static void ghepManh(Player player) {
        int[][] recipes = getRecipes();
        if (InventoryService.gI().getCountEmptyBag(player) <= 0) {
            Service.gI().sendThongBao(player, "Hành trang cần ít nhất 1 chỗ trống");
            return;
        }
        if (player.combineNew.itemsCombine.size() != 4) {
            return;
        }

        // Kiểm tra vàng và ngọc
        int goldCost = getGoldCost();
        int gemCost = getGemCost();
        if (player.inventory.gold < goldCost) {
            Service.gI().sendThongBao(player, "Không đủ vàng để thực hiện");
            return;
        }
        if (player.inventory.gem < gemCost) {
            Service.gI().sendThongBao(player, "Không đủ ngọc để thực hiện");
            return;
        }

        // Tìm recipe
        int recipeIdx = findRecipeIndex(player.combineNew.itemsCombine.get(0).template.id, recipes);
        if (recipeIdx < 0) {
            return;
        }

        // Verify đủ 4 mảnh khác nhau
        boolean[] found = new boolean[4];
        for (Item item : player.combineNew.itemsCombine) {
            for (int j = 0; j < 4; j++) {
                if (item.template.id == recipes[recipeIdx][j] && !found[j]) {
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

        // Trừ chi phí theo cấu hình runtime.
        player.inventory.subGold(goldCost);
        player.inventory.subGem(gemCost);

        if (Util.isTrue((float) getSuccessRate(), 100)) {
            // Thành công: trừ toàn bộ 4 mảnh
            for (Item item : player.combineNew.itemsCombine) {
                InventoryService.gI().subQuantityItemsBag(player, item, 1);
            }

            // Tạo rương kết quả
            short resultId = (short) recipes[recipeIdx][4];
            Item ruong = ItemService.gI().createNewItem(resultId);
            InventoryService.gI().addItemBag(player, ruong);

            // Gửi hiệu ứng hào quang xuất hiện icon vật phẩm (giống ghép Ngọc Rồng)
            CombineService.gI().sendEffectCombineDB(player, (short) ruong.template.iconID);
            Service.gI().sendThongBao(player, "Ghép mảnh thành công! Bạn nhận được " + ruong.template.name);
        } else {
            // Thất bại: mất số mảnh ngẫu nhiên theo cấu hình.
            int failLostFragments = getFailLostFragments();
            List<Integer> indices = new ArrayList<>(List.of(0, 1, 2, 3));
            Collections.shuffle(indices);
            StringBuilder lostNames = new StringBuilder();
            for (int i = 0; i < failLostFragments; i++) {
                Item lostItem = player.combineNew.itemsCombine.get(indices.get(i));
                if (lostNames.length() > 0) {
                    lostNames.append(", ");
                }
                lostNames.append(lostItem.template.name);
                InventoryService.gI().subQuantityItemsBag(player, lostItem, 1);
            }

            // Gửi hiệu ứng thất bại
            CombineService.gI().sendEffectFailCombine(player);
            Service.gI().sendThongBao(player, "Ghép mảnh thất bại! Bạn bị mất " + failLostFragments + " mảnh ("
                    + lostNames + ") và giữ lại được " + (4 - failLostFragments) + " mảnh.");
        }

        Service.gI().sendMoney(player);
        InventoryService.gI().sendItemBags(player);
        CombineService.gI().reOpenItemCombine(player);
    }
}
