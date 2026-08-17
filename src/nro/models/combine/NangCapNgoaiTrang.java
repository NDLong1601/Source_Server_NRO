package nro.models.combine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import nro.models.consts.ConstNpc;
import nro.models.item.Item;
import nro.models.item.Item.ItemOption;
import nro.models.player.Player;
import nro.models.services.InventoryService;
import nro.models.services.ItemService;
import nro.models.services.Service;
import nro.models.utils.Logger;
import nro.models.utils.Util;

/**
 * Hệ thống Nâng cấp ngoại trang (Cải trang, Ván bay, Đeo lưng, Pet).
 */
public final class NangCapNgoaiTrang {

    public static final int OPTION_UPGRADE_LEVEL = 72;
    public static final int OPTION_DOWNGRADE_COUNT = 209;

    private static final int DEFAULT_CLOVER_3_ID = 2056;
    private static final int DEFAULT_CLOVER_4_ID = 2057;
    private static final int DEFAULT_STAR_1_ID = 2058;
    private static final int DEFAULT_STAR_2_ID = 2059;
    private static final int DEFAULT_PROTECTION_ID = 2061;

    private NangCapNgoaiTrang() {
    }

    public static int getProtectionItemId() {
        return CombineConfig.getInt("appearance.upgrade.protectionItemId", DEFAULT_PROTECTION_ID);
    }

    public static int getClover3ItemId() {
        return CombineConfig.getInt("appearance.upgrade.clover3ItemId", DEFAULT_CLOVER_3_ID);
    }

    public static int getClover4ItemId() {
        return CombineConfig.getInt("appearance.upgrade.clover4ItemId", DEFAULT_CLOVER_4_ID);
    }

    public static int getStar1ItemId() {
        return CombineConfig.getInt("appearance.upgrade.star1ItemId", DEFAULT_STAR_1_ID);
    }

    public static int getStar2ItemId() {
        return CombineConfig.getInt("appearance.upgrade.star2ItemId", DEFAULT_STAR_2_ID);
    }

    public static int getUpgradeLevel(Item item) {
        if (item == null || item.itemOptions == null) {
            return 0;
        }
        for (ItemOption io : item.itemOptions) {
            if (io.optionTemplate.id == OPTION_UPGRADE_LEVEL) {
                return Math.max(0, io.param);
            }
        }
        return 0;
    }

    public static void setUpgradeLevel(Item item, int level) {
        if (item == null || item.itemOptions == null) {
            return;
        }
        ItemOption levelOpt = null;
        for (ItemOption io : item.itemOptions) {
            if (io.optionTemplate.id == OPTION_UPGRADE_LEVEL) {
                levelOpt = io;
                break;
            }
        }
        if (levelOpt == null) {
            if (level > 0) {
                item.itemOptions.add(new ItemOption(OPTION_UPGRADE_LEVEL, level));
            }
        } else {
            if (level <= 0) {
                levelOpt.param = 0;
            } else {
                levelOpt.param = level;
            }
        }
    }

    private static Map<Integer, Integer> getOptionSteps() {
        return CombineConfig.getIntMap("appearance.upgrade.optionSteps");
    }

    private static Map<Integer, Integer> getOptionCaps() {
        return CombineConfig.getIntMap("appearance.upgrade.optionCaps");
    }

    public static boolean isEligibleStatOption(ItemOption io) {
        if (io == null || io.optionTemplate == null) {
            return false;
        }
        int id = io.optionTemplate.id;
        if (id == OPTION_UPGRADE_LEVEL || id == OPTION_DOWNGRADE_COUNT
                || id == 93 || id == 73 || id == 30 || id == 86 || id == 87
                || (id >= 127 && id <= 144) || id == 233 || id == 237 || id == 241 || id == 245
                || id == 102 || id == 107) {
            return false;
        }
        Map<Integer, Integer> steps = getOptionSteps();
        if (steps != null && !steps.isEmpty()) {
            return steps.containsKey(id) && steps.get(id) > 0;
        }
        return io.param > 0;
    }

    public static List<ItemOption> getEligibleOptions(Item item) {
        List<ItemOption> list = new ArrayList<>();
        if (item == null || item.itemOptions == null) {
            return list;
        }
        Map<Integer, Integer> caps = getOptionCaps();
        for (ItemOption io : item.itemOptions) {
            if (isEligibleStatOption(io)) {
                int cap = caps.getOrDefault(io.optionTemplate.id, 32767);
                if (io.param < cap) {
                    list.add(io);
                }
            }
        }
        return list;
    }

    public static final class UpgradeSelection {
        public Item target;
        public Item stone;
        public Item clover;
        public Item star;
        public Item protection;
        public AppearanceUpgradeCategory category;
        public boolean valid;
        public String errorMessage;
    }

    public static final class UpgradePlan {
        public int currentLevel;
        public int nextLevel;
        public int stoneId;
        public int stoneCost;
        public long goldCost;
        public int gemCost;
        public double baseRate;
        public double cloverBonus;
        public double finalRate;
        public boolean downgradeRisk;
        public int extraOptionsCount;
        public double starTriggerRate;
        public boolean valid;
        public String errorMessage;
    }

    public static UpgradeSelection parseSelection(Player player) {
        UpgradeSelection sel = new UpgradeSelection();
        if (player.combineNew == null || player.combineNew.itemsCombine == null) {
            sel.valid = false;
            sel.errorMessage = "Chưa chọn vật phẩm";
            return sel;
        }

        List<Item> items = player.combineNew.itemsCombine;
        if (items.isEmpty()) {
            sel.valid = false;
            sel.errorMessage = "Hãy chọn ngoại trang và nguyên liệu cần nâng cấp";
            return sel;
        }

        int protId = getProtectionItemId();
        int c3Id = getClover3ItemId();
        int c4Id = getClover4ItemId();
        int s1Id = getStar1ItemId();
        int s2Id = getStar2ItemId();

        for (Item item : items) {
            if (item == null || !item.isNotNullItem()) {
                continue;
            }
            int tid = item.template.id;

            if (tid == protId || tid == 987) {
                if (sel.protection != null) {
                    sel.valid = false;
                    sel.errorMessage = "Chỉ được dùng tối đa 1 vật phẩm bảo hộ";
                    return sel;
                }
                sel.protection = item;
            } else if (tid == c3Id || tid == c4Id) {
                if (sel.clover != null) {
                    sel.valid = false;
                    sel.errorMessage = "Chỉ được dùng tối đa 1 loại Cỏ may mắn";
                    return sel;
                }
                sel.clover = item;
            } else if (tid == s1Id || tid == s2Id) {
                if (sel.star != null) {
                    sel.valid = false;
                    sel.errorMessage = "Chỉ được dùng tối đa 1 loại Sao may mắn";
                    return sel;
                }
                sel.star = item;
            } else if (AppearanceUpgradeCategory.isAnyUpgradeStone(item)) {
                if (sel.stone != null) {
                    sel.valid = false;
                    sel.errorMessage = "Chỉ được chọn 1 loại đá nâng cấp";
                    return sel;
                }
                sel.stone = item;
            } else {
                AppearanceUpgradeCategory cat = AppearanceUpgradeCategory.categorize(item);
                if (cat != null) {
                    if (sel.target != null) {
                        sel.valid = false;
                        sel.errorMessage = "Chỉ được chọn 1 ngoại trang để nâng cấp";
                        return sel;
                    }
                    sel.target = item;
                    sel.category = cat;
                } else {
                    sel.valid = false;
                    sel.errorMessage = "Vật phẩm '" + item.template.name + "' không hợp lệ";
                    return sel;
                }
            }
        }

        if (sel.target == null) {
            sel.valid = false;
            sel.errorMessage = "Thiếu ngoại trang cần nâng cấp (Cải trang, Ván bay, Đeo lưng hoặc Pet)";
            return sel;
        }

        if (sel.stone == null) {
            sel.valid = false;
            sel.errorMessage = "Thiếu đá nâng cấp phù hợp cho " + sel.category.getDisplayName();
            return sel;
        }

        if (sel.stone.template.id != sel.category.getStoneId()) {
            Item tempStone = ItemService.gI().createNewItem((short) sel.category.getStoneId());
            String expectedName = tempStone != null && tempStone.template != null ? tempStone.template.name : "đúng loại đá";
            sel.valid = false;
            sel.errorMessage = sel.category.getDisplayName() + " cần sử dụng " + expectedName;
            return sel;
        }

        sel.valid = true;
        return sel;
    }

    public static UpgradePlan buildPlan(Player player, UpgradeSelection sel) {
        UpgradePlan plan = new UpgradePlan();
        if (!sel.valid) {
            plan.valid = false;
            plan.errorMessage = sel.errorMessage;
            return plan;
        }

        int currentLvl = getUpgradeLevel(sel.target);
        int maxLvl = CombineConfig.getInt("appearance.upgrade.maxLevel", 10);
        if (currentLvl >= maxLvl) {
            plan.valid = false;
            plan.errorMessage = "Vật phẩm đã đạt cấp nâng cấp tối đa (+" + maxLvl + ")";
            return plan;
        }

        if (sel.target.itemOptions.isEmpty()) {
            List<ItemOption> shopOps = ItemService.gI().getListOptionItemShop((short) sel.target.template.id);
            if (shopOps != null && !shopOps.isEmpty()) {
                for (ItemOption so : shopOps) {
                    sel.target.itemOptions.add(new ItemOption(so.optionTemplate.id, so.param));
                }
            }
        }

        List<ItemOption> eligible = getEligibleOptions(sel.target);
        if (eligible.isEmpty()) {
            plan.valid = false;
            plan.errorMessage = sel.target.template.name + " không có dòng chỉ số (options) nào để nâng cấp";
            return plan;
        }

        String catKey = sel.category.getConfigKey();
        plan.currentLevel = currentLvl;
        plan.nextLevel = currentLvl + 1;
        plan.stoneId = sel.category.getStoneId();

        plan.stoneCost = CombineConfig.getLevelInt("appearance." + catKey + ".stoneCosts", currentLvl, 1, 2, 3, 5, 8, 12, 18, 25, 35, 50);
        plan.goldCost = (long) CombineConfig.getLevelValue("appearance." + catKey + ".goldCosts", currentLvl, 1000000, 2000000, 5000000, 10000000, 20000000, 40000000, 80000000, 150000000, 250000000, 400000000);
        plan.gemCost = CombineConfig.getLevelInt("appearance." + catKey + ".gemCosts", currentLvl, 0);
        plan.baseRate = CombineConfig.getLevelRate("appearance." + catKey + ".successRates", currentLvl, 100, 80, 60, 40, 25, 15, 10, 5, 3, 1);

        plan.cloverBonus = 0;
        if (sel.clover != null) {
            if (sel.clover.template.id == getClover3ItemId()) {
                plan.cloverBonus = CombineConfig.getDouble("appearance.upgrade.clover3BonusRate", 15.0);
            } else if (sel.clover.template.id == getClover4ItemId()) {
                plan.cloverBonus = CombineConfig.getDouble("appearance.upgrade.clover4BonusRate", 30.0);
            }
        }

        double rateCap = CombineConfig.getDouble("appearance.upgrade.rateCap", 95.0);
        if (plan.baseRate >= 100.0) {
            plan.finalRate = 100.0;
        } else {
            plan.finalRate = Math.min(rateCap, plan.baseRate + plan.cloverBonus);
        }

        int[] downgradeLevels = CombineConfig.getIntArray("appearance.upgrade.downgradeLevels", 2, 4, 6, 8);
        plan.downgradeRisk = false;
        for (int dl : downgradeLevels) {
            if (currentLvl == dl) {
                plan.downgradeRisk = true;
                break;
            }
        }

        plan.extraOptionsCount = 0;
        plan.starTriggerRate = 0;
        if (sel.star != null) {
            if (sel.star.template.id == getStar1ItemId()) {
                plan.starTriggerRate = CombineConfig.getDouble("appearance.upgrade.star1ExtraOptionRate", 20.0);
                plan.extraOptionsCount = CombineConfig.getInt("appearance.upgrade.star1ExtraOptionCount", 1);
            } else if (sel.star.template.id == getStar2ItemId()) {
                plan.starTriggerRate = CombineConfig.getDouble("appearance.upgrade.star2ExtraOptionRate", 50.0);
                plan.extraOptionsCount = CombineConfig.getInt("appearance.upgrade.star2ExtraOptionCount", 2);
            }
        }

        plan.valid = true;
        return plan;
    }

    public static boolean hasEnoughResources(Player player, UpgradeSelection sel, UpgradePlan plan) {
        if (player == null || sel == null || plan == null || !plan.valid) {
            return false;
        }
        if (sel.stone == null || sel.stone.quantity < plan.stoneCost) {
            return false;
        }
        if (player.inventory.gold < plan.goldCost) {
            return false;
        }
        if (plan.gemCost > 0 && player.inventory.gem < plan.gemCost) {
            return false;
        }
        return true;
    }

    public static boolean selectedItemsStillInBag(Player player, UpgradeSelection sel) {
        if (player == null || sel == null || sel.target == null || sel.stone == null) {
            return false;
        }
        if (InventoryService.gI().getIndexItemBag(player, sel.target) == -1) {
            return false;
        }
        if (InventoryService.gI().getIndexItemBag(player, sel.stone) == -1) {
            return false;
        }
        if (sel.clover != null && InventoryService.gI().getIndexItemBag(player, sel.clover) == -1) {
            return false;
        }
        if (sel.star != null && InventoryService.gI().getIndexItemBag(player, sel.star) == -1) {
            return false;
        }
        if (sel.protection != null && InventoryService.gI().getIndexItemBag(player, sel.protection) == -1) {
            return false;
        }
        return true;
    }

    public static String buildPreviewText(Player player, UpgradeSelection sel, UpgradePlan plan) {
        StringBuilder sb = new StringBuilder();
        sb.append("|2|").append(sel.target.template.name)
          .append(" (+").append(plan.currentLevel).append(" -> +").append(plan.nextLevel).append(")\n");

        sb.append("|7|Tỉ lệ thành công: ").append(String.format("%.1f", plan.finalRate)).append("%\n");

        if (plan.cloverBonus > 0) {
            sb.append("|6|Cỏ may mắn: +").append((int) plan.cloverBonus).append("% tỉ lệ\n");
        }

        if (plan.extraOptionsCount > 0) {
            sb.append("|6|Sao may mắn: ").append((int) plan.starTriggerRate).append("% tăng ")
              .append(plan.extraOptionsCount).append(" dòng chỉ số\n");
        }

        if (plan.downgradeRisk) {
            if (sel.protection != null) {
                sb.append("|6|Khiên bảo hộ: Chống rớt cấp\n");
            } else {
                sb.append("|7|Thất bại: Rớt về (+").append(plan.currentLevel - 1).append(")\n");
            }
        }

        boolean hasEnoughStone = sel.stone != null && sel.stone.quantity >= plan.stoneCost;
        sb.append(hasEnoughStone ? "|1|" : "|7|")
          .append("Cần ").append(plan.stoneCost).append(" ").append(sel.stone.template.name)
          .append(" (có ").append(sel.stone != null ? sel.stone.quantity : 0).append(")\n");

        boolean hasEnoughGold = player.inventory.gold >= plan.goldCost;
        sb.append(hasEnoughGold ? "|1|" : "|7|")
          .append("Cần ").append(Util.numberToMoney(plan.goldCost)).append(" vàng");

        if (plan.gemCost > 0) {
            boolean hasEnoughGem = player.inventory.gem >= plan.gemCost;
            sb.append("\n").append(hasEnoughGem ? "|1|" : "|7|")
              .append("Cần ").append(plan.gemCost).append(" ngọc");
        }

        return sb.toString();
    }

    public static void showInfoCombine(Player player) {
        if (player == null || player.combineNew == null) {
            return;
        }
        UpgradeSelection sel = parseSelection(player);
        if (!sel.valid) {
            CombineService.gI().baHatMit.createOtherMenu(player, ConstNpc.IGNORE_MENU, sel.errorMessage, "Đóng");
            return;
        }

        UpgradePlan plan = buildPlan(player, sel);
        if (!plan.valid) {
            CombineService.gI().baHatMit.createOtherMenu(player, ConstNpc.IGNORE_MENU, plan.errorMessage, "Đóng");
            return;
        }

        String preview = buildPreviewText(player, sel, plan);
        if (!hasEnoughResources(player, sel, plan)) {
            CombineService.gI().baHatMit.createOtherMenu(player, ConstNpc.IGNORE_MENU, preview + "\n|7|Chưa đủ nguyên liệu nâng cấp", "Đóng");
            return;
        }

        CombineService.gI().baHatMit.createOtherMenu(
                player,
                ConstNpc.MENU_START_COMBINE,
                preview,
                "Nâng cấp\n" + Util.numberToMoney(plan.goldCost) + " vàng",
                "Từ chối"
        );
    }

    public static void nangCapNgoaiTrang(Player player) {
        if (player == null || player.combineNew == null) {
            return;
        }

        synchronized (player.combineNew) {
            long now = System.currentTimeMillis();
            if (now - player.combineNew.lastTimeCombine < 300) {
                return;
            }
            player.combineNew.lastTimeCombine = now;

            UpgradeSelection sel = parseSelection(player);
            if (!sel.valid) {
                Service.gI().sendThongBao(player, sel.errorMessage);
                return;
            }

            if (!selectedItemsStillInBag(player, sel)) {
                Service.gI().sendThongBao(player, "Vật phẩm đã bị thay đổi trong hành trang");
                return;
            }

            UpgradePlan plan = buildPlan(player, sel);
            if (!plan.valid) {
                Service.gI().sendThongBao(player, plan.errorMessage);
                return;
            }

            if (!hasEnoughResources(player, sel, plan)) {
                Service.gI().sendThongBao(player, "Không đủ nguyên liệu hoặc chi phí nâng cấp");
                return;
            }

            // Deduct basic costs
            player.inventory.gold -= plan.goldCost;
            if (plan.gemCost > 0) {
                player.inventory.gem -= plan.gemCost;
            }
            InventoryService.gI().subQuantityItemsBag(player, sel.stone, plan.stoneCost);
            if (sel.clover != null) {
                InventoryService.gI().subQuantityItemsBag(player, sel.clover, 1);
            }
            if (sel.star != null) {
                InventoryService.gI().subQuantityItemsBag(player, sel.star, 1);
            }

            boolean success = Util.isTrue((float) plan.finalRate, 100);
            boolean preventedDowngrade = false;

            if (success) {
                // Apply upgrade
                int nextLvl = plan.nextLevel;
                setUpgradeLevel(sel.target, nextLvl);

                // Upgrade option lines: 1 base option line + extra from Star
                int linesToUpgrade = 1;
                if (plan.extraOptionsCount > 0 && Util.isTrue((float) plan.starTriggerRate, 100)) {
                    linesToUpgrade += plan.extraOptionsCount;
                }

                List<ItemOption> eligible = getEligibleOptions(sel.target);
                Collections.shuffle(eligible);
                int upgradedCount = 0;
                Map<Integer, Integer> caps = getOptionCaps();
                Map<Integer, Integer> steps = getOptionSteps();
                List<String> upgradedNames = new ArrayList<>();

                for (ItemOption io : eligible) {
                    if (upgradedCount >= linesToUpgrade) {
                        break;
                    }
                    int cap = caps.getOrDefault(io.optionTemplate.id, 32767);
                    if (io.param < cap) {
                        int step = Math.max(1, steps.getOrDefault(io.optionTemplate.id, 1));
                        io.param = Math.min(cap, io.param + step);
                        upgradedCount++;
                        upgradedNames.add(io.optionTemplate.name.replaceAll("#", String.valueOf(io.param)));
                    }
                }

                CombineService.gI().sendEffectCombineDB(player, (short) sel.target.template.iconID);
                String msg = "Nâng cấp " + sel.target.template.name + " lên (+" + nextLvl + ") thành công!";
                if (!upgradedNames.isEmpty()) {
                    msg += " Tăng chỉ số: " + String.join(", ", upgradedNames);
                }
                Service.gI().sendThongBao(player, msg);

            } else {
                // Failed upgrade
                if (plan.downgradeRisk) {
                    if (sel.protection != null) {
                        preventedDowngrade = true;
                        // Deduct protection item
                        InventoryService.gI().subQuantityItemsBag(player, sel.protection, 1);
                        CombineService.gI().sendEffectFailCombine(player);
                        Service.gI().sendThongBao(player, "Nâng cấp thất bại, Khiên bảo hộ đã ngăn trang bị tụt cấp!");
                    } else {
                        // Downgrade level by 1
                        int downgradedLvl = Math.max(0, plan.currentLevel - 1);
                        setUpgradeLevel(sel.target, downgradedLvl);

                        // Increment downgrade counter option 209
                        ItemOption dOpt = null;
                        for (ItemOption io : sel.target.itemOptions) {
                            if (io.optionTemplate.id == OPTION_DOWNGRADE_COUNT) {
                                dOpt = io;
                                break;
                            }
                        }
                        if (dOpt == null) {
                            sel.target.itemOptions.add(new ItemOption(OPTION_DOWNGRADE_COUNT, 1));
                        } else {
                            dOpt.param += 1;
                        }

                        // Reduce 1 stat param if possible
                        List<ItemOption> eligible = getEligibleOptions(sel.target);
                        if (!eligible.isEmpty()) {
                            ItemOption io = eligible.get(0);
                            if (io.param > 1) {
                                io.param -= 1;
                            }
                        }

                        CombineService.gI().sendEffectFailCombine(player);
                        Service.gI().sendThongBao(player, "Nâng cấp thất bại, trang bị bị rớt xuống (+" + downgradedLvl + ")!");
                    }
                } else {
                    CombineService.gI().sendEffectFailCombine(player);
                    Service.gI().sendThongBao(player, "Nâng cấp thất bại, chúc bạn may mắn lần sau!");
                }
            }

            // Giữ lại các vật phẩm còn tồn tại trong hành trang để người chơi tiếp tục nâng cấp
            player.combineNew.itemsCombine.removeIf(it -> it == null || !it.isNotNullItem() || !player.inventory.itemsBag.contains(it));

            // Sync bags and money
            InventoryService.gI().sendItemBags(player);
            Service.gI().point(player);
            Service.gI().Send_Caitrang(player);
            Service.gI().sendFlagBag(player);
            player.sendNewPet();
            Service.gI().sendMoney(player);
            CombineService.gI().reOpenItemCombine(player);
        }
    }
}
