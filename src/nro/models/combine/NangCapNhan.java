package nro.models.combine;

import nro.models.consts.ConstNpc;
import nro.models.item.Item;
import nro.models.player.Player;
import nro.models.services.InventoryService;
import nro.models.services.ItemService;
import nro.models.services.Service;
import nro.models.utils.Util;

/** Nâng cấp chuỗi nhẫn đệ tử bằng Đá hoàng kim. */
public final class NangCapNhan {

    public static final int FIRST_RING_ID = 2017;
    public static final int LAST_RING_ID = 2025;
    public static final int GOLDEN_STONE_ID = 2026;

    private static final int[] DEFAULT_STONE_COSTS = {1, 2, 3, 4, 5, 6, 7, 8};
    private static final int[] DEFAULT_GOLD_COSTS = {0, 0, 0, 0, 0, 0, 0, 0};
    private static final int[] DEFAULT_GEM_COSTS = {0, 0, 0, 0, 0, 0, 0, 0};
    private static final double[] DEFAULT_SUCCESS_RATES = {100, 100, 100, 100, 100, 100, 100, 100};

    private NangCapNhan() {
    }

    public static boolean isRing(Item item) {
        return item != null && item.isNotNullItem() && isRingTemplate(item.template.id);
    }

    public static boolean isRingTemplate(int templateId) {
        return templateId >= FIRST_RING_ID && templateId <= LAST_RING_ID;
    }

    private static Item findRing(Player player) {
        for (Item item : player.combineNew.itemsCombine) {
            if (isRing(item)) {
                return item;
            }
        }
        return null;
    }

    private static Item findGoldenStone(Player player) {
        for (Item item : player.combineNew.itemsCombine) {
            if (item != null && item.isNotNullItem() && item.template.id == GOLDEN_STONE_ID) {
                return item;
            }
        }
        return null;
    }

    public static void showInfoCombine(Player player) {
        boolean stoneEnabled = isStoneEnabled();
        boolean goldEnabled = isGoldEnabled();
        boolean gemEnabled = isGemEnabled();
        int expectedItems = stoneEnabled ? 2 : 1;
        if (player.combineNew.itemsCombine.size() != expectedItems) {
            showInvalidSelection(player);
            return;
        }

        Item ring = findRing(player);
        Item stone = findGoldenStone(player);
        if (ring == null || (stoneEnabled && stone == null) || (!stoneEnabled && stone != null)) {
            showInvalidSelection(player);
            return;
        }

        int level = ring.template.id - FIRST_RING_ID;
        if (level >= LAST_RING_ID - FIRST_RING_ID) {
            CombineService.gI().baHatMit.createOtherMenu(player, ConstNpc.IGNORE_MENU,
                    "Nhẫn của ngươi đã đạt cấp tối đa", "Đóng");
            return;
        }

        int stoneCost = getStoneCost(level);
        int goldCost = getGoldCost(level);
        int gemCost = getGemCost(level);
        float successRate = (float) getSuccessRate(level);
        player.combineNew.countDaNangCap = stoneCost;
        player.combineNew.goldCombine = goldCost;
        player.combineNew.gemCombine = gemCost;
        player.combineNew.ratioCombine = successRate;

        StringBuilder text = new StringBuilder();
        text.append("|2|").append(ring.template.name).append(" [Cấp ").append(level).append("]\n");
        text.append("|2|Sau nâng cấp: ")
                .append(ItemService.gI().getTemplate((short) (ring.template.id + 1)).name)
                .append(" [Cấp ").append(level + 1).append("]\n");
        text.append("|2|Tỉ lệ thành công: ").append(successRate).append("%\n");
        boolean hasResourceLine = false;
        if (stoneEnabled) {
            text.append(stone.quantity < stoneCost ? "|7|" : "|1|")
                    .append("Cần ").append(stoneCost).append(" ").append(stone.template.name);
            hasResourceLine = true;
        }
        if (goldEnabled) {
            if (hasResourceLine) {
                text.append("\n");
            }
            text.append(player.inventory.gold < goldCost ? "|7|" : "|1|")
                    .append("Cần ").append(Util.numberToMoney(goldCost)).append(" vàng");
            hasResourceLine = true;
        }
        if (gemEnabled) {
            if (hasResourceLine) {
                text.append("\n");
            }
            text.append(player.inventory.gem < gemCost ? "|7|" : "|1|")
                    .append("Cần ").append(Util.numberToMoney(gemCost)).append(" ngọc xanh");
        }

        if (stoneEnabled && stone.quantity < stoneCost) {
            CombineService.gI().baHatMit.createOtherMenu(player, ConstNpc.IGNORE_MENU, text.toString(),
                    "Còn thiếu\n" + (stoneCost - stone.quantity) + " " + stone.template.name);
        } else if (goldEnabled && player.inventory.gold < goldCost) {
            CombineService.gI().baHatMit.createOtherMenu(player, ConstNpc.IGNORE_MENU, text.toString(),
                    "Còn thiếu\n" + Util.numberToMoney(goldCost - player.inventory.gold) + " vàng");
        } else if (gemEnabled && player.inventory.gem < gemCost) {
            CombineService.gI().baHatMit.createOtherMenu(player, ConstNpc.IGNORE_MENU, text.toString(),
                    "Còn thiếu\n" + Util.numberToMoney(gemCost - player.inventory.gem) + " ngọc xanh");
        } else {
            StringBuilder button = new StringBuilder("Nâng cấp");
            if (stoneEnabled) {
                button.append("\n").append(stoneCost).append(" đá");
            }
            if (goldEnabled) {
                button.append("\n").append(Util.numberToMoney(goldCost)).append(" vàng");
            }
            if (gemEnabled) {
                button.append("\n").append(Util.numberToMoney(gemCost)).append(" ngọc");
            }
            CombineService.gI().baHatMit.createOtherMenu(player, ConstNpc.MENU_START_COMBINE,
                    text.toString(), button.toString(), "Từ chối");
        }
    }

    public static void nangCapNhan(Player player) {
        boolean stoneEnabled = isStoneEnabled();
        boolean goldEnabled = isGoldEnabled();
        boolean gemEnabled = isGemEnabled();
        int expectedItems = stoneEnabled ? 2 : 1;
        if (player.combineNew.itemsCombine.size() != expectedItems) {
            return;
        }

        Item ring = findRing(player);
        Item stone = findGoldenStone(player);
        if (ring == null || (stoneEnabled && stone == null) || (!stoneEnabled && stone != null)) {
            return;
        }

        int level = ring.template.id - FIRST_RING_ID;
        if (level < 0 || level >= LAST_RING_ID - FIRST_RING_ID) {
            Service.gI().sendThongBao(player, "Nhẫn đã đạt cấp tối đa");
            return;
        }

        int stoneCost = getStoneCost(level);
        int goldCost = getGoldCost(level);
        int gemCost = getGemCost(level);
        double successRate = getSuccessRate(level);
        if (stoneEnabled && stone.quantity < stoneCost) {
            Service.gI().sendThongBao(player, "Không đủ Đá hoàng kim để thực hiện");
            return;
        }
        if (goldEnabled && player.inventory.gold < goldCost) {
            Service.gI().sendThongBao(player, "Không đủ vàng để thực hiện");
            return;
        }
        if (gemEnabled && player.inventory.gem < gemCost) {
            Service.gI().sendThongBao(player, "Không đủ ngọc xanh để thực hiện");
            return;
        }

        if (stoneEnabled) {
            InventoryService.gI().subQuantityItemsBag(player, stone, stoneCost);
        }
        if (goldEnabled) {
            player.inventory.gold -= goldCost;
        }
        if (gemEnabled) {
            player.inventory.gem -= gemCost;
        }
        if (Util.isTrue((float) successRate, 100)) {
            short nextRingId = (short) (ring.template.id + 1);
            ring.template = ItemService.gI().getTemplate(nextRingId);
            ring.itemOptions.clear();
            ring.itemOptions.addAll(ItemService.gI().getDefaultItemOptions(nextRingId));
            ring.content = ring.getContent();
            ring.info = ring.getInfo();
            CombineService.gI().sendEffectSuccessCombine(player);
            Service.gI().sendThongBao(player, "Nâng cấp thành công " + ring.template.name);
        } else {
            CombineService.gI().sendEffectFailCombine(player);
            Service.gI().sendThongBao(player, "Nâng cấp nhẫn thất bại");
        }

        InventoryService.gI().sendItemBags(player);
        if (goldEnabled || gemEnabled) {
            Service.gI().sendMoney(player);
        }
        if (stoneEnabled && !stone.isNotNullItem()) {
            player.combineNew.clearItemCombine();
        }
        CombineService.gI().reOpenItemCombine(player);
    }

    private static int getStoneCost(int level) {
        return isStoneEnabled()
                ? Math.max(1, CombineConfig.getLevelInt("ring.upgrade.stoneCosts", level, DEFAULT_STONE_COSTS)) : 0;
    }

    private static int getGoldCost(int level) {
        return isGoldEnabled()
                ? Math.max(0, CombineConfig.getLevelInt("ring.upgrade.goldCosts", level, DEFAULT_GOLD_COSTS)) : 0;
    }

    private static int getGemCost(int level) {
        return isGemEnabled()
                ? Math.max(0, CombineConfig.getLevelInt("ring.upgrade.gemCosts", level, DEFAULT_GEM_COSTS)) : 0;
    }

    private static boolean isStoneEnabled() {
        return CombineConfig.getBoolean("ring.upgrade.stoneEnabled", true);
    }

    private static boolean isGoldEnabled() {
        return CombineConfig.getBoolean("ring.upgrade.goldEnabled", false);
    }

    private static boolean isGemEnabled() {
        return CombineConfig.getBoolean("ring.upgrade.gemEnabled", false);
    }

    private static double getSuccessRate(int level) {
        return CombineConfig.getLevelRate("ring.upgrade.successRates", level, DEFAULT_SUCCESS_RATES);
    }

    private static void showInvalidSelection(Player player) {
        String requirement = isStoneEnabled()
                ? "Hãy chọn 1 nhẫn đệ tử và Đá hoàng kim"
                : "Hãy chỉ chọn 1 nhẫn đệ tử";
        CombineService.gI().baHatMit.createOtherMenu(player, ConstNpc.IGNORE_MENU,
                requirement, "Đóng");
    }
}
