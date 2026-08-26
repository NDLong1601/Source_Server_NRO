package nro.models.fishing;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadLocalRandom;
import nro.models.item.Item;
import nro.models.player.ItemEvent;
import nro.models.player.Player;
import nro.models.services.InventoryService;
import nro.models.services.ItemService;
import nro.models.services.ItemTimeService;
import nro.models.services.Service;
import nro.models.utils.Util;

/**
 * Server-authoritative core for the Fishing Event.
 *
 * <p>The first production version deliberately uses the existing item-use
 * protocol: click a rod once to cast and once more after the bite message to
 * pull. It keeps RNG, the bite time, and every reward on the server, so a
 * later dedicated client UI can reuse this service without changing rates or
 * reward code.</p>
 */
public final class FishingService {

    private static final FishingService INSTANCE = new FishingService();

    private static final int MAP_FISHING_VILLAGE = 186;
    private static final int MAP_FISHING_PORT = 192;
    private static final int MAP_FISHING_1 = 193;
    private static final int MAP_FISHING_2 = 194;
    private static final int MAP_FISHING_3 = 195;

    private static final long BITE_DELAY_MIN_MILLIS = 1_500L;
    private static final long BITE_DELAY_MAX_MILLIS = 2_500L;
    private static final long REACTION_WINDOW_MILLIS = 1_500L;
    private static final long PERFECT_WINDOW_MILLIS = 250L;
    private static final int FISHING_SUPPORT_ITEM_DURATION_SECONDS = 15 * 60;

    public static final int CRAFT_REPAIR_KIT = 0;
    public static final int CRAFT_BAIT_BOX = 1;
    public static final int CRAFT_CRYSTAL_BAIT = 2;
    public static final int CRAFT_FISHER_CHEST = 3;

    private final Map<Long, FishingSession> sessions = new ConcurrentHashMap<>();
    private final ScheduledExecutorService notifier;

    private FishingService() {
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "fishing-bite-notifier");
            thread.setDaemon(true);
            return thread;
        };
        notifier = Executors.newSingleThreadScheduledExecutor(factory);
    }

    public static FishingService gI() {
        return INSTANCE;
    }

    public boolean useRod(Player player, Item rod) {
        if (player == null || rod == null || rod.template == null) {
            return false;
        }
        synchronized (player) {
            FishingSession active = sessions.get(player.id);
            if (active == null) {
                return startFishing(player, rod.template.id);
            }
            if (active.rodId != rod.template.id) {
                Service.gI().sendThongBao(player, "Hãy dùng đúng cần câu đang thả.");
                return false;
            }
            return pullFishing(player, active);
        }
    }

    public void selectBait(Player player, Item bait) {
        if (bait == null || bait.template == null) {
            return;
        }
        short itemId = bait.template.id;
        if (!ensureNotFishing(player) || !FishingItems.isBait(itemId) || !hasInBag(player, itemId)) {
            return;
        }
        markEquippedItem(player, bait);
        player.itemEvent.fishingBaitId = itemId;
        Service.gI().sendThongBao(player, "Đã chọn " + ItemService.gI().getTemplate(itemId).name + " làm mồi câu.");
    }

    public void selectGear(Player player, Item gear) {
        if (gear == null || gear.template == null) {
            return;
        }
        short itemId = gear.template.id;
        if (!ensureNotFishing(player) || !hasInBag(player, itemId)) {
            return;
        }
        ItemEvent data = player.itemEvent;
        if (FishingItems.isLine(itemId)) {
            data.fishingLineId = itemId;
        } else if (FishingItems.isFloat(itemId)) {
            data.fishingFloatId = itemId;
        } else if (FishingItems.isReel(itemId)) {
            data.fishingReelId = itemId;
        } else if (FishingItems.isHook(itemId)) {
            data.fishingHookId = itemId;
        } else {
            return;
        }
        markEquippedItem(player, gear);
        Service.gI().sendThongBao(player, "Đã trang bị " + ItemService.gI().getTemplate(itemId).name + ".");
    }

    /**
     * Keeps the marker on one concrete bag item. This matters if a player has
     * several otherwise-identical bait/tackle stacks: only the clicked stack
     * receives the client-side glow and the "Đang sử dụng" tooltip entry.
     */
    private void markEquippedItem(Player player, Item selectedItem) {
        int selectedItemId = selectedItem.template.id;
        for (Item item : player.inventory.itemsBag) {
            if (!item.isNotNullItem() || item.template == null
                    || !FishingItems.isSameEquipmentSlot(item.template.id, selectedItemId)) {
                continue;
            }
            removeEquippedMarker(item);
        }
        selectedItem.itemOptions.add(new Item.ItemOption(
                FishingItems.EQUIPPED_MARKER_OPTION_ID,
                FishingItems.EQUIPPED_MARKER_EFFECT));
    }

    private void removeEquippedMarker(Item item) {
        for (int index = item.itemOptions.size() - 1; index >= 0; index--) {
            Item.ItemOption option = item.itemOptions.get(index);
            if (option != null && option.optionTemplate != null
                    && option.optionTemplate.id == FishingItems.EQUIPPED_MARKER_OPTION_ID) {
                item.itemOptions.remove(index);
            }
        }
    }

    public boolean useFishFinder(Player player) {
        if (!ensureNotFishing(player)) {
            return false;
        }
        player.itemEvent.fishingFishFinderExpiresAt = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(15);
        ItemTimeService.gI().removeItemTime(player, FishingItems.FISH_FINDER);
        ItemTimeService.gI().sendItemTime(player, ItemService.gI().getTemplate(FishingItems.FISH_FINDER).iconID,
                FISHING_SUPPORT_ITEM_DURATION_SECONDS);
        Service.gI().sendThongBao(player, "Máy dò cá hoạt động trong 15 phút.");
        return true;
    }

    public boolean useLuckyCharm(Player player) {
        if (!ensureNotFishing(player)) {
            return false;
        }
        player.itemEvent.fishingLuckyCharmExpiresAt = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(15);
        ItemTimeService.gI().removeItemTime(player, FishingItems.LUCKY_CHARM);
        ItemTimeService.gI().sendItemTime(player, ItemService.gI().getTemplate(FishingItems.LUCKY_CHARM).iconID,
                FISHING_SUPPORT_ITEM_DURATION_SECONDS);
        Service.gI().sendThongBao(player, "Bùa May Mắn Ngư Phủ hoạt động trong 15 phút.");
        return true;
    }

    public boolean openFisherChest(Player player, Item chest) {
        if (!ensureNotFishing(player) || chest == null || InventoryService.gI().getCountEmptyBag(player) < 1) {
            if (player != null && InventoryService.gI().getCountEmptyBag(player) < 1) {
                Service.gI().sendThongBao(player, "Cần ít nhất 1 ô trống trong hành trang để mở rương.");
            }
            return false;
        }
        int chance = ThreadLocalRandom.current().nextInt(100);
        short itemId;
        int quantity = 1;
        if (chance < 30) {
            itemId = FishingItems.FISHER_COIN;
            quantity = ThreadLocalRandom.current().nextInt(10, 31);
        } else if (chance < 60) {
            itemId = new short[]{FishingItems.BAIT_WORM, FishingItems.BAIT_DOUGH, FishingItems.BAIT_LARVA}
                    [ThreadLocalRandom.current().nextInt(3)];
            quantity = ThreadLocalRandom.current().nextInt(2, 6);
        } else if (chance < 75) {
            itemId = new short[]{FishingItems.LINE_MONOFILAMENT, FishingItems.FLOAT_WOOD}
                    [ThreadLocalRandom.current().nextInt(2)];
        } else if (chance < 85) {
            itemId = FishingItems.HOOK_STEEL;
        } else if (chance < 93) {
            itemId = FishingItems.REEL_OLD;
        } else if (chance < 97) {
            itemId = FishingItems.FISH_FINDER;
        } else if (chance < 99) {
            itemId = FishingItems.LUCKY_CHARM;
        } else {
            itemId = FishingItems.BAIT_BOX;
        }
        InventoryService.gI().subQuantityItemsBag(player, chest, 1);
        grant(player, itemId, quantity);
        InventoryService.gI().sendItemBags(player);
        Service.gI().sendThongBao(player, "Rương Ngư Phủ mở ra: " + ItemService.gI().getTemplate(itemId).name + ".");
        return true;
    }

    public boolean claimStarterKit(Player player) {
        if (player == null) {
            return false;
        }
        synchronized (player) {
            if (player.itemEvent.fishingStarterClaimed) {
                Service.gI().sendThongBao(player, "Bạn đã nhận bộ câu khởi đầu rồi.");
                return false;
            }
            if (InventoryService.gI().getCountEmptyBag(player) < 5) {
                Service.gI().sendThongBao(player, "Cần ít nhất 5 ô trống trong hành trang.");
                return false;
            }
            grant(player, FishingItems.ROD_CRUDE, 1);
            grant(player, FishingItems.BAIT_WORM, 20);
            grant(player, FishingItems.LINE_MONOFILAMENT, 1);
            grant(player, FishingItems.FLOAT_WOOD, 1);
            grant(player, FishingItems.REEL_OLD, 1);
            ItemEvent data = player.itemEvent;
            data.fishingStarterClaimed = true;
            data.fishingBaitId = FishingItems.BAIT_WORM;
            data.fishingLineId = FishingItems.LINE_MONOFILAMENT;
            data.fishingFloatId = FishingItems.FLOAT_WOOD;
            data.fishingReelId = FishingItems.REEL_OLD;
            data.fishingHookId = -1;
            InventoryService.gI().sendItemBags(player);
            Service.gI().sendThongBao(player, "Đã nhận Bộ câu khởi đầu. Đến Khu Câu cá 1 và dùng Cần Câu Cùi để bắt đầu.");
            return true;
        }
    }

    public int exchangeAllFish(Player player) {
        if (player == null) {
            return 0;
        }
        synchronized (player) {
            int coins = 0;
            for (FishDefinition fish : FISH) {
                coins += redeemFish(player, fish.itemId, fish.coinValue, 1);
                coins += redeemFish(player, FishingItems.giantFishIdFor(fish.itemId), fish.coinValue, 3);
            }
            if (coins > 0) {
                if (hasInBag(player, FishingItems.OCEAN_CRATE)) {
                    coins = coins * 105 / 100;
                } else if (hasInBag(player, FishingItems.FISH_BASKET)) {
                    coins = coins * 103 / 100;
                }
                grant(player, FishingItems.FISHER_COIN, coins);
                InventoryService.gI().sendItemBags(player);
                Service.gI().sendThongBao(player, "Đã đổi cá nhận " + coins + " Xu Ngư Phủ.");
            } else {
                Service.gI().sendThongBao(player, "Bạn chưa có cá nào để đổi.");
            }
            return coins;
        }
    }

    public boolean purchase(Player player, short itemId, int quantity, int coinCost, int badgeCost) {
        return purchase(player, itemId, quantity, coinCost, badgeCost, (short) -1);
    }

    public boolean upgradeRod(Player player, short currentRod, short upgradedRod, int coinCost, int badgeCost) {
        return purchase(player, upgradedRod, 1, coinCost, badgeCost, currentRod);
    }

    public int cleanPollution(Player player) {
        if (player == null) {
            return 0;
        }
        synchronized (player) {
            int points = 0;
            points += consumeAll(player, FishingItems.JUNK_BANANA_PEEL) * 1;
            points += consumeAll(player, FishingItems.JUNK_SMALL_TRASH_BAG) * 2;
            points += consumeAll(player, FishingItems.JUNK_LARGE_TRASH_BAG) * 4;
            points += consumeAll(player, FishingItems.JUNK_SCRAP_METAL) * 2;
            points += consumeAll(player, FishingItems.JUNK_TORN_NET) * 3;
            if (points == 0) {
                Service.gI().sendThongBao(player, "Bạn chưa có rác ô nhiễm để làm sạch.");
                return 0;
            }
            player.itemEvent.fishingCleaningPoints += points;
            InventoryService.gI().sendItemBags(player);
            Service.gI().sendThongBao(player, "Đã làm sạch biển, nhận " + points + " Điểm Làm Sạch. Tổng: "
                    + player.itemEvent.fishingCleaningPoints + ".");
            return points;
        }
    }

    public boolean redeemCleaningReward(Player player, int reward) {
        if (player == null) {
            return false;
        }
        synchronized (player) {
            resetDailyCounters(player.itemEvent);
            int pointCost;
            short itemId;
            int quantity = 1;
            boolean chestReward = false;
            switch (reward) {
                case 0 -> {
                    pointCost = 10;
                    itemId = FishingItems.FISHER_COIN;
                    quantity = 10;
                }
                case 1 -> {
                    pointCost = 25;
                    itemId = FishingItems.BAIT_BOX;
                }
                case 2 -> {
                    pointCost = 50;
                    itemId = FishingItems.FISHER_CHEST;
                    chestReward = true;
                }
                case 3 -> {
                    pointCost = 100;
                    itemId = FishingItems.FISHER_CHEST;
                    chestReward = true;
                }
                default -> {
                    return false;
                }
            }
            if (player.itemEvent.fishingCleaningPoints < pointCost) {
                Service.gI().sendThongBao(player, "Bạn chưa đủ Điểm Làm Sạch.");
                return false;
            }
            if (chestReward && player.itemEvent.fishingCleaningChestsToday >= 2) {
                Service.gI().sendThongBao(player, "Bạn đã đổi đủ 2 Rương Ngư Phủ bằng Điểm Làm Sạch hôm nay.");
                return false;
            }
            if (!canReceive(player, itemId, (short) -1)) {
                Service.gI().sendThongBao(player, "Hành trang cần thêm một ô trống.");
                return false;
            }
            player.itemEvent.fishingCleaningPoints -= pointCost;
            if (chestReward) {
                player.itemEvent.fishingCleaningChestsToday++;
            }
            grant(player, itemId, quantity);
            if (reward == 3) {
                grant(player, FishingItems.FISHER_BADGE, 1);
            }
            InventoryService.gI().sendItemBags(player);
            Service.gI().sendThongBao(player, "Đổi Điểm Làm Sạch thành công.");
            return true;
        }
    }

    public boolean craft(Player player, int recipe) {
        if (player == null) {
            return false;
        }
        synchronized (player) {
            resetDailyCounters(player.itemEvent);
            short reward;
            int rewardQuantity = 1;
            int coinCost;
            int pointCost = 0;
            int pollutionCost = 0;
            short[] materials;
            int[] materialQuantities;
            switch (recipe) {
                case CRAFT_REPAIR_KIT -> {
                    reward = FishingItems.REPAIR_KIT;
                    coinCost = 20;
                    materials = new short[]{FishingItems.JUNK_SCRAP_METAL, FishingItems.JUNK_TORN_NET, FishingItems.JUNK_SEAWEED};
                    materialQuantities = new int[]{2, 2, 1};
                }
                case CRAFT_BAIT_BOX -> {
                    reward = FishingItems.BAIT_BOX;
                    coinCost = 10;
                    pointCost = 5;
                    materials = new short[]{FishingItems.JUNK_SEAWEED, FishingItems.JUNK_CORAL};
                    materialQuantities = new int[]{2, 1};
                }
                case CRAFT_CRYSTAL_BAIT -> {
                    reward = FishingItems.BAIT_CRYSTAL;
                    rewardQuantity = 3;
                    coinCost = 20;
                    materials = new short[]{FishingItems.JUNK_CORAL, FishingItems.JUNK_SEAWEED};
                    materialQuantities = new int[]{2, 2};
                }
                case CRAFT_FISHER_CHEST -> {
                    if (player.itemEvent.fishingCleaningChestsToday >= 2) {
                        Service.gI().sendThongBao(player, "Bạn đã chế đủ 2 Rương Ngư Phủ hôm nay.");
                        return false;
                    }
                    reward = FishingItems.FISHER_CHEST;
                    coinCost = 50;
                    pointCost = 20;
                    pollutionCost = 10;
                    materials = new short[0];
                    materialQuantities = new int[0];
                }
                default -> {
                    return false;
                }
            }
            if (!hasQuantity(player, FishingItems.FISHER_COIN, coinCost)
                    || player.itemEvent.fishingCleaningPoints < pointCost
                    || !hasMaterials(player, materials, materialQuantities)
                    || (pollutionCost > 0 && pollutionCount(player) < pollutionCost)) {
                Service.gI().sendThongBao(player, "Nguyên liệu hoặc Xu Ngư Phủ chưa đủ để chế tạo.");
                return false;
            }
            if (!canReceive(player, reward, (short) -1)) {
                Service.gI().sendThongBao(player, "Hành trang cần thêm một ô trống.");
                return false;
            }
            consumeQuantity(player, FishingItems.FISHER_COIN, coinCost);
            for (int index = 0; index < materials.length; index++) {
                consumeQuantity(player, materials[index], materialQuantities[index]);
            }
            if (pollutionCost > 0) {
                consumePollution(player, pollutionCost);
                player.itemEvent.fishingCleaningChestsToday++;
            }
            player.itemEvent.fishingCleaningPoints -= pointCost;
            grant(player, reward, rewardQuantity);
            InventoryService.gI().sendItemBags(player);
            Service.gI().sendThongBao(player, "Chế tạo thành công: " + ItemService.gI().getTemplate(reward).name + ".");
            return true;
        }
    }

    private boolean startFishing(Player player, short rodId) {
        if (!isFishingMap(player)) {
            Service.gI().sendThongBao(player,
                    "Chỉ có thể câu tại Làng Chài, Khu vực đánh bắt hoặc một trong ba Khu Câu cá.");
            return false;
        }
        if (InventoryService.gI().getCountEmptyBag(player) < 1) {
            Service.gI().sendThongBao(player, "Hành trang cần ít nhất 1 ô trống trước khi câu.");
            return false;
        }
        int rodTier = rodMaxFishTier(rodId);
        short baitId = selectedBait(player);
        BaitDefinition bait = getBait(baitId);
        if (bait == null || !hasInBag(player, baitId)) {
            Service.gI().sendThongBao(player, "Bạn chưa chọn mồi câu hợp lệ.");
            return false;
        }
        if (bait.minTier > rodTier) {
            Service.gI().sendThongBao(player, "Cần câu hiện tại quá yếu để sử dụng loại mồi này.");
            return false;
        }
        if (eligibleFish(player.zone.map.mapId, rodTier, bait).isEmpty()) {
            Service.gI().sendThongBao(player, "Bộ cần và mồi này không phù hợp với ngư trường hiện tại.");
            return false;
        }

        Item baitItem = InventoryService.gI().findItemBag(player, baitId);
        boolean preserveBait = hasInBag(player, FishingItems.BAIT_BOX) && roll(10);
        if (!preserveBait) {
            InventoryService.gI().subQuantityItemsBag(player, baitItem, 1);
        }

        Outcome outcome = rollOutcome(player, bait);
        long biteAt = System.currentTimeMillis() + randomBetween(BITE_DELAY_MIN_MILLIS, BITE_DELAY_MAX_MILLIS);
        FishingSession session = new FishingSession(rodId, baitId, player.zone.map.mapId, outcome, biteAt,
                biteAt + reactionWindow(player));
        sessions.put(player.id, session);
        InventoryService.gI().sendItemBags(player);
        Service.gI().sendThongBao(player, preserveBait
                ? "Bạn thả câu xuống nước. Hộp Mồi đã giữ lại mồi lần này."
                : "Bạn thả câu xuống nước. Hãy chờ phao động rồi dùng cần lần nữa để kéo.");

        notifier.schedule(() -> notifyBite(player, session), biteAt - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
        notifier.schedule(() -> expireSession(player, session), session.expiresAt - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
        return true;
    }

    private boolean pullFishing(Player player, FishingSession session) {
        long now = System.currentTimeMillis();
        if (!isFishingMap(player) || player.zone.map.mapId != session.mapId) {
            sessions.remove(player.id, session);
            Service.gI().sendThongBao(player, "Bạn đã rời ngư trường, lượt câu bị hủy.");
            return false;
        }
        if (now < session.biteAt) {
            Service.gI().sendThongBao(player, "Phao chưa động, hãy chờ thêm một chút.");
            return false;
        }
        if (now > session.expiresAt) {
            sessions.remove(player.id, session);
            resetCombo(player);
            Service.gI().sendThongBao(player, "Bạn kéo quá muộn, con cá đã thoát mất.");
            return false;
        }
        session.timingBonus = now - session.biteAt <= PERFECT_WINDOW_MILLIS ? 10 : 5;
        sessions.remove(player.id, session);
        if (session.outcome == Outcome.JUNK) {
            return resolveJunk(player, session);
        }
        if (session.outcome == Outcome.SPECIAL) {
            return resolveSpecial(player);
        }
        BaitDefinition bait = getBait(session.baitId);
        if (bait == null) {
            resetCombo(player);
            Service.gI().sendThongBao(player, "Mồi câu không còn hợp lệ, lượt câu bị hủy.");
            return false;
        }
        session.fish = rollFish(player, rodMaxFishTier(session.rodId), bait);
        if (session.fish == null) {
            resetCombo(player);
            Service.gI().sendThongBao(player, "Không tìm thấy loài cá phù hợp với bộ câu hiện tại.");
            return false;
        }
        session.giant = roll(1);
        return resolveFish(player, session, session.timingBonus);
    }

    private boolean resolveFish(Player player, FishingSession session, int timingBonus) {
        FishDefinition fish = session.fish;
        int success = clamp(fish.basePull + rodPullBonus(session.rodId) + reelPullBonus(selectedReel(player))
                + timingBonus - (session.giant ? 20 : 0), 5, 95);
        if (!roll(success)) {
            resetCombo(player);
            Service.gI().sendThongBao(player, "Cá đã vùng vẫy và thoát mất.");
            return false;
        }
        if (roll(lineBreakRate(selectedLine(player), fish.tier))) {
            Item repair = InventoryService.gI().findItemBag(player, FishingItems.REPAIR_KIT);
            if (repair == null) {
                resetCombo(player);
                Service.gI().sendThongBao(player, "Dây câu đã đứt, bạn đánh mất con cá.");
                return false;
            }
            InventoryService.gI().subQuantityItemsBag(player, repair, 1);
            Service.gI().sendThongBao(player, "Bộ Sửa Cần Câu đã cứu lượt câu này.");
        }
        short caughtItemId = session.giant ? FishingItems.giantFishIdFor(fish.itemId) : fish.itemId;
        if (!grant(player, caughtItemId, 1)) {
            resetCombo(player);
            Service.gI().sendThongBao(player, "Hành trang không còn chỗ để nhận cá.");
            return false;
        }
        FishingProgressService.gI().recordCatch(player, fish.itemId, session.giant);
        player.itemEvent.fishingCombo = Math.min(20, player.itemEvent.fishingCombo + 1);
        String giantLabel = session.giant ? " Khổng Lồ" : "";
        Service.gI().sendThongBao(player, "Bạn đã câu được " + fish.name + giantLabel
                + "! Combo: " + player.itemEvent.fishingCombo + ".");
        rollClusterHook(player, fish);
        InventoryService.gI().sendItemBags(player);
        return true;
    }

    private boolean resolveJunk(Player player, FishingSession session) {
        short itemId = weightedJunk();
        if (isHeavyJunk(itemId) && roll(heavyJunkBreakRate(selectedLine(player)))) {
            Item repair = InventoryService.gI().findItemBag(player, FishingItems.REPAIR_KIT);
            if (repair == null) {
                Service.gI().sendThongBao(player, "Đồ quá nặng làm đứt dây; combo của bạn vẫn được giữ.");
                return false;
            }
            InventoryService.gI().subQuantityItemsBag(player, repair, 1);
        }
        grant(player, itemId, 1);
        InventoryService.gI().sendItemBags(player);
        Service.gI().sendThongBao(player, "Bạn kéo lên " + ItemService.gI().getTemplate(itemId).name + ". Combo được giữ nguyên.");
        return true;
    }

    private boolean resolveSpecial(Player player) {
        int roll = ThreadLocalRandom.current().nextInt(100);
        short itemId;
        int quantity = 1;
        if (roll < 40) {
            itemId = FishingItems.FISHER_COIN;
            quantity = ThreadLocalRandom.current().nextInt(5, 26);
        } else if (roll < 57) {
            itemId = FishingItems.FISHER_BADGE;
        } else if (roll < 70) {
            itemId = FishingItems.REPAIR_KIT;
        } else if (roll < 90) {
            itemId = FishingItems.FISHER_CHEST;
        } else {
            itemId = FishingItems.BAIT_BOX;
        }
        resetDailyCounters(player.itemEvent);
        if (itemId == FishingItems.FISHER_CHEST) {
            if (player.itemEvent.fishingDirectChestsToday >= 5) {
                itemId = FishingItems.FISHER_COIN;
                quantity = ThreadLocalRandom.current().nextInt(5, 26);
            } else {
                player.itemEvent.fishingDirectChestsToday++;
            }
        } else if (itemId == FishingItems.FISHER_BADGE) {
            if (player.itemEvent.fishingDirectBadgesToday >= 20) {
                itemId = FishingItems.FISHER_COIN;
                quantity = ThreadLocalRandom.current().nextInt(5, 26);
            } else {
                player.itemEvent.fishingDirectBadgesToday++;
            }
        }
        grant(player, itemId, quantity);
        InventoryService.gI().sendItemBags(player);
        Service.gI().sendThongBao(player, "Bạn câu được vật phẩm đặc biệt: "
                + ItemService.gI().getTemplate(itemId).name + ".");
        return true;
    }

    private void rollClusterHook(Player player, FishDefinition primaryFish) {
        short hook = selectedHook(player);
        int extraFish;
        if (hook == FishingItems.HOOK_STEEL) {
            extraFish = roll(10) ? 1 : 0;
        } else if (hook == FishingItems.HOOK_JADE) {
            extraFish = roll(20) ? 1 : 0;
        } else if (hook == FishingItems.HOOK_GOLDEN) {
            int chance = ThreadLocalRandom.current().nextInt(100);
            extraFish = chance < 5 ? 2 : chance < 35 ? 1 : 0;
        } else {
            return;
        }
        if (extraFish == 0) {
            return;
        }
        int maxTier = primaryFish.tier >= 14 ? 13 : primaryFish.tier;
        int minTier = Math.max(1, primaryFish.tier - 2);
        int grantedFish = 0;
        for (int index = 0; index < extraFish; index++) {
            FishDefinition extra = randomFishFromTierRange(minTier, maxTier);
            if (extra != null && grant(player, extra.itemId, 1)) {
                FishingProgressService.gI().recordCatch(player, extra.itemId, false);
                grantedFish++;
            }
        }
        if (grantedFish > 0) {
            Service.gI().sendThongBao(player, "Lưỡi câu chùm mang về thêm " + grantedFish + " cá phụ.");
        }
    }

    private Outcome rollOutcome(Player player, BaitDefinition bait) {
        int fishRate = 82 + bait.fishModifier;
        int junkRate = 15 + bait.junkModifier;
        int specialRate = 3 + bait.specialModifier;
        short floatId = selectedFloat(player);
        if (floatId == FishingItems.FLOAT_JADE) {
            fishRate++;
            junkRate--;
        } else if (floatId == FishingItems.FLOAT_SEA_GOD) {
            fishRate++;
            junkRate -= 2;
            specialRate++;
        }
        long now = System.currentTimeMillis();
        if (player.itemEvent.fishingFishFinderExpiresAt > now) {
            fishRate += 2;
            junkRate -= 2;
        }
        if (player.itemEvent.fishingLuckyCharmExpiresAt > now) {
            junkRate--;
            specialRate++;
        }
        junkRate = Math.max(6, junkRate);
        specialRate = Math.min(6, specialRate);
        fishRate = 100 - junkRate - specialRate;
        int result = ThreadLocalRandom.current().nextInt(100);
        if (result < fishRate) {
            return Outcome.FISH;
        }
        return result < fishRate + junkRate ? Outcome.JUNK : Outcome.SPECIAL;
    }

    private FishDefinition rollFish(Player player, int rodTier, BaitDefinition bait) {
        List<WeightedFish> weighted = new ArrayList<>();
        for (FishDefinition fish : eligibleFish(player.zone.map.mapId, rodTier, bait)) {
            double multiplier = 1.0D;
            if (bait.itemId == FishingItems.BAIT_GOLDEN_CARP) {
                if (fish.tier >= 14) {
                    multiplier += 0.50D;
                } else if (fish.tier >= 11) {
                    multiplier += 0.30D;
                }
            }
            short floatId = selectedFloat(player);
            if (floatId == FishingItems.FLOAT_JADE && fish.tier >= 8) {
                multiplier += 0.10D;
            } else if (floatId == FishingItems.FLOAT_SEA_GOD) {
                if (fish.tier >= 14) {
                    multiplier += 0.30D;
                } else if (fish.tier >= 10) {
                    multiplier += 0.20D;
                }
            }
            long now = System.currentTimeMillis();
            if (player.itemEvent.fishingFishFinderExpiresAt > now) {
                if (fish.tier == 13) {
                    multiplier += 0.20D;
                } else if (fish.tier >= 14) {
                    multiplier += 0.10D;
                } else if (fish.tier >= 10) {
                    multiplier += 0.30D;
                }
            }
            if (player.itemEvent.fishingLuckyCharmExpiresAt > now) {
                if (fish.tier == 15) {
                    multiplier += 0.30D;
                } else if (fish.tier == 14) {
                    multiplier += 0.25D;
                } else if (fish.tier >= 11) {
                    multiplier += 0.15D;
                }
            }
            int combo = player.itemEvent.fishingCombo;
            if (combo >= 20) {
                multiplier += 0.10D;
            } else if (combo >= 10) {
                multiplier += 0.05D;
            } else if (combo >= 5) {
                multiplier += 0.03D;
            }
            multiplier = Math.min(2.0D, multiplier);
            weighted.add(new WeightedFish(fish, Math.max(1, (int) Math.round(fish.weight * multiplier))));
        }
        return weightedRollFish(weighted);
    }

    private List<FishDefinition> eligibleFish(int mapId, int rodTier, BaitDefinition bait) {
        List<FishDefinition> result = new ArrayList<>();
        int[] mapRange = mapTierRange(mapId);
        for (FishDefinition fish : FISH) {
            if (fish.tier >= mapRange[0] && fish.tier <= mapRange[1]
                    && fish.tier <= rodTier && fish.tier >= bait.minTier && fish.tier <= bait.maxTier) {
                result.add(fish);
            }
        }
        return result;
    }

    private FishDefinition weightedRollFish(List<WeightedFish> fish) {
        int total = 0;
        for (WeightedFish entry : fish) {
            total += entry.weight;
        }
        if (total <= 0) {
            return null;
        }
        int roll = ThreadLocalRandom.current().nextInt(total);
        int current = 0;
        for (WeightedFish entry : fish) {
            current += entry.weight;
            if (roll < current) {
                return entry.fish;
            }
        }
        return fish.get(fish.size() - 1).fish;
    }

    private FishDefinition randomFishFromTierRange(int minTier, int maxTier) {
        List<WeightedFish> options = new ArrayList<>();
        for (FishDefinition fish : FISH) {
            if (fish.tier >= minTier && fish.tier <= maxTier) {
                options.add(new WeightedFish(fish, fish.weight));
            }
        }
        return weightedRollFish(options);
    }

    private short weightedJunk() {
        int result = ThreadLocalRandom.current().nextInt(100);
        if (result < 18) return FishingItems.JUNK_BANANA_PEEL;
        if (result < 34) return FishingItems.JUNK_SMALL_TRASH_BAG;
        if (result < 42) return FishingItems.JUNK_LARGE_TRASH_BAG;
        if (result < 49) return FishingItems.JUNK_STONE;
        if (result < 59) return FishingItems.JUNK_CORAL;
        if (result < 75) return FishingItems.JUNK_SEAWEED;
        if (result < 87) return FishingItems.JUNK_SCRAP_METAL;
        return FishingItems.JUNK_TORN_NET;
    }

    private void notifyBite(Player player, FishingSession session) {
        if (sessions.get(player.id) == session && System.currentTimeMillis() <= session.expiresAt) {
            Service.gI().sendThongBao(player, "Phao đang động! Dùng lại cần câu ngay để kéo.");
        }
    }

    private void expireSession(Player player, FishingSession session) {
        if (sessions.remove(player.id, session)) {
            resetCombo(player);
            Service.gI().sendThongBao(player, "Bạn không kéo cần kịp lúc nên cá đã thoát mất.");
        }
    }

    private boolean ensureNotFishing(Player player) {
        if (player == null) {
            return false;
        }
        if (sessions.containsKey(player.id)) {
            Service.gI().sendThongBao(player, "Bạn đang trong một lượt câu.");
            return false;
        }
        return true;
    }

    private boolean isFishingMap(Player player) {
        return player != null && player.zone != null && player.zone.map != null
                && (player.zone.map.mapId == MAP_FISHING_VILLAGE
                || player.zone.map.mapId == MAP_FISHING_PORT
                || player.zone.map.mapId == MAP_FISHING_1
                || player.zone.map.mapId == MAP_FISHING_2
                || player.zone.map.mapId == MAP_FISHING_3);
    }

    private int[] mapTierRange(int mapId) {
        return switch (mapId) {
            case MAP_FISHING_VILLAGE, MAP_FISHING_PORT, MAP_FISHING_1, MAP_FISHING_2, MAP_FISHING_3 ->
                new int[]{1, 15};
            default -> new int[]{1, 0};
        };
    }

    private int reactionWindow(Player player) {
        short floatId = selectedFloat(player);
        if (floatId == FishingItems.FLOAT_SEA_GOD) return (int) REACTION_WINDOW_MILLIS + 1_000;
        if (floatId == FishingItems.FLOAT_JADE) return (int) REACTION_WINDOW_MILLIS + 500;
        return (int) REACTION_WINDOW_MILLIS;
    }

    private short selectedBait(Player player) {
        return FishingItems.isBait(player.itemEvent.fishingBaitId) ? player.itemEvent.fishingBaitId : FishingItems.BAIT_WORM;
    }

    private short selectedLine(Player player) {
        return FishingItems.isLine(player.itemEvent.fishingLineId) ? player.itemEvent.fishingLineId : FishingItems.LINE_MONOFILAMENT;
    }

    private short selectedFloat(Player player) {
        return FishingItems.isFloat(player.itemEvent.fishingFloatId) ? player.itemEvent.fishingFloatId : FishingItems.FLOAT_WOOD;
    }

    private short selectedReel(Player player) {
        return FishingItems.isReel(player.itemEvent.fishingReelId) ? player.itemEvent.fishingReelId : FishingItems.REEL_OLD;
    }

    private short selectedHook(Player player) {
        return FishingItems.isHook(player.itemEvent.fishingHookId) ? player.itemEvent.fishingHookId : -1;
    }

    private int rodMaxFishTier(short rodId) {
        return switch (rodId) {
            case FishingItems.ROD_CRUDE -> 4;
            case FishingItems.ROD_COMMON -> 7;
            case FishingItems.ROD_ADVANCED -> 10;
            case FishingItems.ROD_ROYAL -> 13;
            case FishingItems.ROD_SEA_KING -> 15;
            default -> 0;
        };
    }

    private int rodPullBonus(short rodId) {
        return switch (rodId) {
            case FishingItems.ROD_COMMON -> 5;
            case FishingItems.ROD_ADVANCED -> 10;
            case FishingItems.ROD_ROYAL -> 15;
            case FishingItems.ROD_SEA_KING -> 20;
            default -> 0;
        };
    }

    private int reelPullBonus(short reelId) {
        return switch (reelId) {
            case FishingItems.REEL_ADVANCED -> 10;
            case FishingItems.REEL_SEA_KING -> 15;
            default -> 5;
        };
    }

    private int lineBreakRate(short lineId, int fishTier) {
        if (lineId == FishingItems.LINE_DRAGON_SCALE) return fishTier >= 13 ? 1 : 0;
        if (lineId == FishingItems.LINE_STEEL) return fishTier >= 13 ? 5 : fishTier >= 8 ? 2 : 0;
        return fishTier >= 13 ? 10 : fishTier >= 8 ? 5 : 1;
    }

    private int heavyJunkBreakRate(short lineId) {
        if (lineId == FishingItems.LINE_DRAGON_SCALE) return 0;
        if (lineId == FishingItems.LINE_STEEL) return 1;
        return 2;
    }

    private boolean isHeavyJunk(short itemId) {
        return itemId == FishingItems.JUNK_STONE || itemId == FishingItems.JUNK_LARGE_TRASH_BAG
                || itemId == FishingItems.JUNK_TORN_NET;
    }

    private BaitDefinition getBait(short itemId) {
        for (BaitDefinition bait : BAITS) {
            if (bait.itemId == itemId) {
                return bait;
            }
        }
        return null;
    }

    private boolean hasInBag(Player player, short itemId) {
        return InventoryService.gI().findItemBag(player, itemId) != null;
    }

    private boolean purchase(Player player, short itemId, int quantity, int coinCost, int badgeCost,
            short requiredItemId) {
        if (player == null || quantity <= 0) {
            return false;
        }
        synchronized (player) {
            if (!hasQuantity(player, FishingItems.FISHER_COIN, coinCost)
                    || !hasQuantity(player, FishingItems.FISHER_BADGE, badgeCost)) {
                Service.gI().sendThongBao(player, "Bạn chưa đủ Xu Ngư Phủ hoặc Huy Hiệu Ngư Phủ.");
                return false;
            }
            if (requiredItemId >= 0 && !hasQuantity(player, requiredItemId, 1)) {
                Service.gI().sendThongBao(player, "Bạn cần sở hữu trang bị cấp trước để nâng cấp.");
                return false;
            }
            if (!canReceive(player, itemId, requiredItemId)) {
                Service.gI().sendThongBao(player, "Hành trang cần thêm một ô trống.");
                return false;
            }
            consumeQuantity(player, FishingItems.FISHER_COIN, coinCost);
            consumeQuantity(player, FishingItems.FISHER_BADGE, badgeCost);
            if (requiredItemId >= 0) {
                consumeQuantity(player, requiredItemId, 1);
            }
            grant(player, itemId, quantity);
            InventoryService.gI().sendItemBags(player);
            Service.gI().sendThongBao(player, "Đã nhận " + ItemService.gI().getTemplate(itemId).name + ".");
            return true;
        }
    }

    private boolean hasMaterials(Player player, short[] itemIds, int[] quantities) {
        for (int index = 0; index < itemIds.length; index++) {
            if (!hasQuantity(player, itemIds[index], quantities[index])) {
                return false;
            }
        }
        return true;
    }

    private boolean hasQuantity(Player player, short itemId, int quantity) {
        if (quantity <= 0) {
            return true;
        }
        int total = 0;
        for (Item item : player.inventory.itemsBag) {
            if (item != null && item.isNotNullItem() && item.template.id == itemId) {
                total += item.quantity;
                if (total >= quantity) {
                    return true;
                }
            }
        }
        return false;
    }

    private void consumeQuantity(Player player, short itemId, int quantity) {
        int remaining = quantity;
        for (Item item : new ArrayList<>(player.inventory.itemsBag)) {
            if (remaining <= 0) {
                return;
            }
            if (item != null && item.isNotNullItem() && item.template.id == itemId) {
                int take = Math.min(item.quantity, remaining);
                InventoryService.gI().subQuantityItemsBag(player, item, take);
                remaining -= take;
            }
        }
    }

    private int consumeAll(Player player, short itemId) {
        int amount = 0;
        for (Item item : new ArrayList<>(player.inventory.itemsBag)) {
            if (item != null && item.isNotNullItem() && item.template.id == itemId) {
                amount += item.quantity;
                InventoryService.gI().subQuantityItemsBag(player, item, item.quantity);
            }
        }
        return amount;
    }

    private int pollutionCount(Player player) {
        return itemCount(player, FishingItems.JUNK_BANANA_PEEL)
                + itemCount(player, FishingItems.JUNK_SMALL_TRASH_BAG)
                + itemCount(player, FishingItems.JUNK_LARGE_TRASH_BAG)
                + itemCount(player, FishingItems.JUNK_SCRAP_METAL)
                + itemCount(player, FishingItems.JUNK_TORN_NET);
    }

    private int itemCount(Player player, short itemId) {
        int total = 0;
        for (Item item : player.inventory.itemsBag) {
            if (item != null && item.isNotNullItem() && item.template.id == itemId) {
                total += item.quantity;
            }
        }
        return total;
    }

    private void consumePollution(Player player, int quantity) {
        int remaining = quantity;
        for (short itemId : new short[]{FishingItems.JUNK_BANANA_PEEL, FishingItems.JUNK_SMALL_TRASH_BAG,
            FishingItems.JUNK_LARGE_TRASH_BAG, FishingItems.JUNK_SCRAP_METAL, FishingItems.JUNK_TORN_NET}) {
            int take = Math.min(itemCount(player, itemId), remaining);
            consumeQuantity(player, itemId, take);
            remaining -= take;
            if (remaining == 0) {
                return;
            }
        }
    }

    private boolean canReceive(Player player, short itemId, short consumedItemId) {
        Item sameItem = InventoryService.gI().findItemBag(player, itemId);
        if (sameItem != null && sameItem.template.isUpToUp) {
            return true;
        }
        return consumedItemId >= 0 || InventoryService.gI().getCountEmptyBag(player) > 0;
    }

    private void resetDailyCounters(ItemEvent data) {
        long now = System.currentTimeMillis();
        if (data.fishingDailyResetAt == 0 || !Util.isSameDay(data.fishingDailyResetAt, now)) {
            data.fishingDailyResetAt = now;
            data.fishingDirectChestsToday = 0;
            data.fishingDirectBadgesToday = 0;
            data.fishingCleaningChestsToday = 0;
        }
    }

    private int redeemFish(Player player, short itemId, int coinValue, int multiplier) {
        Item item = InventoryService.gI().findItemBag(player, itemId);
        if (item == null || item.quantity <= 0) {
            return 0;
        }
        int coins = item.quantity * coinValue * multiplier;
        InventoryService.gI().subQuantityItemsBag(player, item, item.quantity);
        return coins;
    }

    private boolean grant(Player player, short itemId, int quantity) {
        Item item = ItemService.gI().createNewItem(itemId, quantity);
        return item != null && InventoryService.gI().addItemBag(player, item);
    }

    private void resetCombo(Player player) {
        player.itemEvent.fishingCombo = 0;
    }

    private boolean roll(int chancePercent) {
        return ThreadLocalRandom.current().nextInt(100) < chancePercent;
    }

    private long randomBetween(long minimum, long maximum) {
        return ThreadLocalRandom.current().nextLong(minimum, maximum + 1L);
    }

    private int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private enum Outcome {
        FISH, JUNK, SPECIAL
    }

    private static final class FishingSession {
        private final short rodId;
        private final short baitId;
        private final int mapId;
        private final Outcome outcome;
        private final long biteAt;
        private final long expiresAt;
        private FishDefinition fish;
        private boolean giant;
        private int timingBonus;

        private FishingSession(short rodId, short baitId, int mapId, Outcome outcome, long biteAt, long expiresAt) {
            this.rodId = rodId;
            this.baitId = baitId;
            this.mapId = mapId;
            this.outcome = outcome;
            this.biteAt = biteAt;
            this.expiresAt = expiresAt;
        }
    }

    private static final class FishDefinition {
        private final short itemId;
        private final int tier;
        private final String name;
        private final int weight;
        private final int basePull;
        private final int coinValue;

        private FishDefinition(short itemId, int tier, String name, int weight, int basePull, int coinValue) {
            this.itemId = itemId;
            this.tier = tier;
            this.name = name;
            this.weight = weight;
            this.basePull = basePull;
            this.coinValue = coinValue;
        }
    }

    private static final class WeightedFish {
        private final FishDefinition fish;
        private final int weight;

        private WeightedFish(FishDefinition fish, int weight) {
            this.fish = fish;
            this.weight = weight;
        }
    }

    private static final class BaitDefinition {
        private final short itemId;
        private final int minTier;
        private final int maxTier;
        private final int fishModifier;
        private final int junkModifier;
        private final int specialModifier;

        private BaitDefinition(short itemId, int minTier, int maxTier, int fishModifier, int junkModifier,
                int specialModifier) {
            this.itemId = itemId;
            this.minTier = minTier;
            this.maxTier = maxTier;
            this.fishModifier = fishModifier;
            this.junkModifier = junkModifier;
            this.specialModifier = specialModifier;
        }
    }

    private static final BaitDefinition[] BAITS = {
        new BaitDefinition(FishingItems.BAIT_WORM, 1, 4, 0, 0, 0),
        new BaitDefinition(FishingItems.BAIT_DOUGH, 1, 6, 0, 0, 0),
        new BaitDefinition(FishingItems.BAIT_LARVA, 1, 8, 0, 0, 0),
        new BaitDefinition(FishingItems.BAIT_SHRIMP, 2, 10, 1, -1, 0),
        new BaitDefinition(FishingItems.BAIT_BLUE_FISH, 3, 12, 2, -2, 0),
        new BaitDefinition(FishingItems.BAIT_CRYSTAL, 5, 14, 1, -2, 1),
        new BaitDefinition(FishingItems.BAIT_GOLDEN_CARP, 7, 15, 2, -3, 1)
    };

    private static final FishDefinition[] FISH = {
        new FishDefinition(FishingItems.FISH_SILVER, 1, "Cá Bạc Nhỏ", 210, 95, 1),
        new FishDefinition(FishingItems.FISH_ROCK_PERCH, 2, "Cá Rô Đá", 160, 95, 2),
        new FishDefinition(FishingItems.FISH_GOLD_CARP, 3, "Cá Chép Vàng", 130, 95, 3),
        new FishDefinition(FishingItems.FISH_PREDATOR_SNAKEHEAD, 4, "Cá Lóc Săn Mồi", 110, 88, 4),
        new FishDefinition(FishingItems.FISH_GIANT_CATFISH, 5, "Cá Trê Khổng Lồ", 90, 88, 5),
        new FishDefinition(FishingItems.FISH_SILVER_SALMON, 6, "Cá Hồi Bạc", 75, 88, 7),
        new FishDefinition(FishingItems.FISH_ARMORED_STURGEON, 7, "Cá Tầm Thiết Giáp", 60, 78, 9),
        new FishDefinition(FishingItems.FISH_TUNA, 8, "Cá Ngừ Đại Dương", 45, 78, 12),
        new FishDefinition(FishingItems.FISH_BLUE_SWORD, 9, "Cá Kiếm Lam", 35, 78, 15),
        new FishDefinition(FishingItems.FISH_WHITE_SHARK, 10, "Cá Mập Trắng", 28, 65, 20),
        new FishDefinition(FishingItems.FISH_HAMMERHEAD, 11, "Cá Mập Đầu Búa", 20, 65, 28),
        new FishDefinition(FishingItems.FISH_WHALE_SHARK, 12, "Cá Mập Voi Khổng Lồ", 15, 65, 40),
        new FishDefinition(FishingItems.FISH_DEEP_SEA_DEMON, 13, "Quỷ Ngư Biển Sâu", 10, 50, 60),
        new FishDefinition(FishingItems.FISH_JADE_SEA_DRAGON, 14, "Hải Long Lam Ngọc", 7, 40, 90),
        new FishDefinition(FishingItems.FISH_SEA_GOD_DRAGON, 15, "Kim Long Hải Thần", 5, 30, 150)
    };
}
