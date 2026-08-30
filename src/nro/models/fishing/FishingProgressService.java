package nro.models.fishing;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.LinkedHashMap;
import java.util.Map;
import nro.models.data.LocalManager;
import nro.models.item.Item;
import nro.models.network.Message;
import nro.models.player.ItemEvent;
import nro.models.player.Player;
import nro.models.services.InventoryService;
import nro.models.services.ItemService;
import nro.models.services.Service;
import nro.models.utils.Logger;
import nro.models.utils.Util;

/** Persistent fish book and four-tier daily fishing missions. */
public final class FishingProgressService {

    public static final byte QUEST_EASY = 0;
    public static final byte QUEST_MEDIUM = 1;
    public static final byte QUEST_HARD = 2;
    public static final byte QUEST_EXTREME = 3;
    public static final int MAX_DAILY_QUESTS = 10;

    private static final String[] FISH_NAMES = {
        "Cá Bạc Nhỏ", "Cá Rô Đá", "Cá Chép Vàng", "Cá Lóc Săn Mồi", "Cá Trê Khổng Lồ",
        "Cá Hồi Bạc", "Cá Tầm Thiết Giáp", "Cá Ngừ Đại Dương", "Cá Kiếm Lam", "Cá Mập Trắng",
        "Cá Mập Đầu Búa", "Cá Mập Voi Khổng Lồ", "Quỷ Ngư Biển Sâu", "Hải Long Lam Ngọc",
        "Kim Long Hải Thần"
    };

    private static final String[] FISH_RARITIES = {
        "Phổ thông", "Phổ thông", "Phổ thông", "Không phổ thông", "Không phổ thông",
        "Không phổ thông", "Hiếm", "Hiếm", "Hiếm", "Cao cấp", "Cao cấp", "Siêu hiếm",
        "Sử thi", "Huyền thoại", "Cực hiếm"
    };

    /**
     * Reused, currently unspawned normal-mob slots.  The native radar screen
     * displays type-0 cards through a mob template, so these IDs provide the
     * matching large fish illustration without increasing the 127-template
     * client limit.
     */
    private static final short[] FISH_BOOK_MOB_IDS = {
        86, 87, 88, 89, 90, 91, 92, 93, 94, 95, 96, 97, 98, 99, 100
    };

    private static final String[] QUEST_NAMES = {"Dễ", "Vừa", "Khó", "Cực khó"};
    private static final int[] QUEST_MIN_TIER = {1, 5, 9, 13};
    private static final int[] QUEST_MAX_TIER = {4, 8, 12, 15};
    private static final int[] QUEST_TARGET = {5, 8, 5, 2};
    private static final long FISH_BOOK_CONFIG_CACHE_MILLIS = 10_000L;

    /**
     * Defers singleton construction until all static fish metadata above has
     * been initialized. The constructor builds default entries from that
     * metadata, so creating it earlier leaves FISH_NAMES null and disconnects
     * the player's menu collector.
     */
    private static final class Holder {
        private static final FishingProgressService INSTANCE = new FishingProgressService();
    }

    private volatile boolean fishBookSchemaReady;
    private volatile FishBookEntry[] fishBookEntries = defaultFishBookEntries();
    private volatile long fishBookEntriesRefreshAt;

    private FishingProgressService() {
    }

    public static FishingProgressService gI() {
        return Holder.INSTANCE;
    }

    public void recordCatch(Player player, short standardFishId, boolean giant) {
        if (player == null || standardFishId < FishingItems.FISH_SILVER
                || standardFishId > FishingItems.FISH_SEA_GOD_DRAGON) {
            return;
        }
        ItemEvent data = player.itemEvent;
        int index = standardFishId - FishingItems.FISH_SILVER;
        int bit = 1 << index;
        boolean firstCatch = (data.fishingFishCaughtMask & bit) == 0;
        data.fishingFishCaughtMask |= bit;
        if (giant) {
            data.fishingGiantFishCaughtMask |= bit;
        }
        if (data.fishingFishCatchCounts[index] < Integer.MAX_VALUE) {
            data.fishingFishCatchCounts[index]++;
        }
        if (firstCatch) {
            Service.gI().sendThongBao(player, "Sổ Tay Ngư Phủ đã ghi nhận loài mới: "
                    + getFishBookEntries()[index].name + ".");
        }
        updateQuestProgress(player, index + 1);
    }

    public void openFishBook(Player player) {
        if (player == null) {
            return;
        }
        Message message = null;
        try {
            message = new Message(127);
            message.writer().writeByte(0);
            message.writer().writeShort(FISH_NAMES.length);
            appendFishBookEntries(message, player);
            message.writer().flush();
            player.sendMessage(message);
        } catch (Exception exception) {
            Logger.logException(FishingProgressService.class, exception, "Không thể mở Sổ Tay Ngư Phủ");
        } finally {
            if (message != null) {
                message.cleanup();
            }
        }
    }

    /**
     * Writes the dedicated fishing codex cards. Their type-0 template is a
     * fish sprite mob, which the native radar screen renders in its preview.
     */
    public void appendFishBookEntries(Message message, Player player) throws Exception {
        FishBookEntry[] entries = getFishBookEntries();
        for (int index = 0; index < FISH_NAMES.length; index++) {
            short itemId = (short) (FishingItems.FISH_SILVER + index);
            int bit = 1 << index;
            boolean caught = (player.itemEvent.fishingFishCaughtMask & bit) != 0;
            boolean giant = (player.itemEvent.fishingGiantFishCaughtMask & bit) != 0;
            int tier = index + 1;
            FishBookEntry entry = entries[index];

            message.writer().writeShort(itemId);
            message.writer().writeShort(ItemService.gI().getTemplate(itemId).iconID);
            message.writer().writeByte(entry.rank);
            message.writer().writeByte(caught ? 1 : 0);
            message.writer().writeByte(1);
            // The native collection screen applies its grey lock state from level 0.
            message.writer().writeByte(0);
            message.writer().writeShort(entry.mobTemplateId);
            message.writer().writeUTF(entry.name);
            message.writer().writeUTF(caught
                    ? "Cấp cá " + tier + " - " + entry.rarity + "\nĐã câu: "
                            + player.itemEvent.fishingFishCatchCounts[index] + " lần"
                            + (giant ? "\nĐã bắt được phiên bản Khổng Lồ." : "\nChưa bắt được phiên bản Khổng Lồ.")
                    : "Chưa khám phá. Hãy câu được loài này để khôi phục màu sắc và ghi nhận vào sổ tay.");
            message.writer().writeByte(caught ? 1 : 0);
            message.writer().writeByte(0);
            message.writer().writeByte(0);
        }
    }

    private FishBookEntry[] getFishBookEntries() {
        long now = System.currentTimeMillis();
        FishBookEntry[] cached = fishBookEntries;
        if (cached != null && now < fishBookEntriesRefreshAt) {
            return cached;
        }
        synchronized (this) {
            now = System.currentTimeMillis();
            if (fishBookEntries != null && now < fishBookEntriesRefreshAt) {
                return fishBookEntries;
            }
            FishBookEntry[] loaded = defaultFishBookEntries();
            try {
                ensureFishBookSchema();
                try (Connection connection = LocalManager.getConnection();
                        PreparedStatement statement = connection.prepareStatement(
                                "SELECT fish_item_id, display_name, rarity, mob_template_id, book_rank "
                                + "FROM fishing_book_entry WHERE fish_item_id BETWEEN ? AND ?")) {
                    statement.setShort(1, FishingItems.FISH_SILVER);
                    statement.setShort(2, FishingItems.FISH_SEA_GOD_DRAGON);
                    try (ResultSet resultSet = statement.executeQuery()) {
                        while (resultSet.next()) {
                            int index = resultSet.getShort("fish_item_id") - FishingItems.FISH_SILVER;
                            if (index < 0 || index >= loaded.length) {
                                continue;
                            }
                            FishBookEntry fallback = loaded[index];
                            String name = nonBlank(resultSet.getString("display_name"), fallback.name);
                            String rarity = nonBlank(resultSet.getString("rarity"), fallback.rarity);
                            short mobTemplateId = (short) Math.max(Short.MIN_VALUE,
                                    Math.min(Short.MAX_VALUE, resultSet.getInt("mob_template_id")));
                            byte rank = (byte) Math.max(0, Math.min(6, resultSet.getInt("book_rank")));
                            loaded[index] = new FishBookEntry(name, rarity, mobTemplateId, rank);
                        }
                    }
                }
            } catch (Exception exception) {
                Logger.logException(FishingProgressService.class, exception,
                        "Không thể tải cấu hình Sổ Tay Ngư Phủ");
            }
            fishBookEntries = loaded;
            fishBookEntriesRefreshAt = now + FISH_BOOK_CONFIG_CACHE_MILLIS;
            return loaded;
        }
    }

    private void ensureFishBookSchema() throws Exception {
        if (fishBookSchemaReady) {
            return;
        }
        synchronized (this) {
            if (fishBookSchemaReady) {
                return;
            }
            LocalManager.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS fishing_book_entry ("
                    + "fish_item_id SMALLINT NOT NULL,"
                    + "display_name VARCHAR(120) NOT NULL,"
                    + "rarity VARCHAR(80) NOT NULL,"
                    + "mob_template_id SMALLINT NOT NULL DEFAULT -1,"
                    + "book_rank TINYINT NOT NULL DEFAULT 0,"
                    + "PRIMARY KEY (fish_item_id)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci");
            LocalManager.executeUpdate(
                    "INSERT IGNORE INTO fishing_book_entry "
                    + "(fish_item_id,display_name,rarity,mob_template_id,book_rank) VALUES "
                    + "(2124,'Cá Bạc Nhỏ','Phổ thông',86,0),"
                    + "(2125,'Cá Rô Đá','Phổ thông',87,0),"
                    + "(2126,'Cá Chép Vàng','Phổ thông',88,0),"
                    + "(2127,'Cá Lóc Săn Mồi','Không phổ thông',89,0),"
                    + "(2128,'Cá Trê Khổng Lồ','Không phổ thông',90,1),"
                    + "(2129,'Cá Hồi Bạc','Không phổ thông',91,1),"
                    + "(2130,'Cá Tầm Thiết Giáp','Hiếm',92,1),"
                    + "(2131,'Cá Ngừ Đại Dương','Hiếm',93,1),"
                    + "(2132,'Cá Kiếm Lam','Hiếm',94,2),"
                    + "(2133,'Cá Mập Trắng','Cao cấp',95,2),"
                    + "(2134,'Cá Mập Đầu Búa','Cao cấp',96,2),"
                    + "(2135,'Cá Mập Voi Khổng Lồ','Siêu hiếm',97,2),"
                    + "(2136,'Quỷ Ngư Biển Sâu','Sử thi',98,3),"
                    + "(2137,'Hải Long Lam Ngọc','Huyền thoại',99,3),"
                    + "(2138,'Kim Long Hải Thần','Cực hiếm',100,3)");
            fishBookSchemaReady = true;
        }
    }

    private static FishBookEntry[] defaultFishBookEntries() {
        FishBookEntry[] entries = new FishBookEntry[FISH_NAMES.length];
        for (int index = 0; index < entries.length; index++) {
            entries[index] = new FishBookEntry(FISH_NAMES[index], FISH_RARITIES[index],
                    FISH_BOOK_MOB_IDS[index], bookRank(index + 1));
        }
        return entries;
    }

    private static String nonBlank(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    public boolean hasActiveQuest(Player player) {
        resetQuestDay(player);
        return player != null && validDifficulty(player.itemEvent.fishingQuestDifficulty);
    }

    public boolean isActiveQuestComplete(Player player) {
        return hasActiveQuest(player)
                && player.itemEvent.fishingQuestProgress >= questTarget(player.itemEvent.fishingQuestDifficulty);
    }

    public boolean acceptQuest(Player player, byte difficulty) {
        if (player == null || !validDifficulty(difficulty)) {
            return false;
        }
        resetQuestDay(player);
        ItemEvent data = player.itemEvent;
        if (validDifficulty(data.fishingQuestDifficulty)) {
            Service.gI().sendThongBao(player, "Hãy hoàn thành nhiệm vụ đang nhận trước.");
            return false;
        }
        if (data.fishingQuestsAcceptedToday >= MAX_DAILY_QUESTS) {
            Service.gI().sendThongBao(player, "Bạn đã dùng đủ " + MAX_DAILY_QUESTS + " lượt nhận nhiệm vụ hôm nay.");
            return false;
        }
        data.fishingQuestsAcceptedToday++;
        data.fishingQuestDifficulty = difficulty;
        data.fishingQuestProgress = 0;
        Service.gI().sendThongBao(player, "Đã nhận nhiệm vụ " + QUEST_NAMES[difficulty] + ": "
                + questObjective(difficulty) + ".");
        return true;
    }

    public boolean claimQuestReward(Player player) {
        if (!isActiveQuestComplete(player)) {
            if (player != null) {
                Service.gI().sendThongBao(player, "Nhiệm vụ chưa hoàn thành.");
            }
            return false;
        }
        ItemEvent data = player.itemEvent;
        byte difficulty = data.fishingQuestDifficulty;
        Map<Short, Integer> rewards = questRewards(difficulty);
        if (requiredEmptySlots(player, rewards) > InventoryService.gI().getCountEmptyBag(player)) {
            Service.gI().sendThongBao(player, "Hành trang không đủ chỗ để nhận toàn bộ phần thưởng nhiệm vụ.");
            return false;
        }
        for (Map.Entry<Short, Integer> reward : rewards.entrySet()) {
            Item item = ItemService.gI().createNewItem(reward.getKey(), reward.getValue());
            if (item == null || !InventoryService.gI().addItemBag(player, item)) {
                Service.gI().sendThongBao(player, "Không thể trao đầy đủ phần thưởng, vui lòng thử lại.");
                return false;
            }
        }
        data.fishingQuestCompletedMask |= 1 << difficulty;
        data.fishingQuestsCompletedToday++;
        data.fishingQuestDifficulty = -1;
        data.fishingQuestProgress = 0;
        InventoryService.gI().sendItemBags(player);
        Service.gI().sendThongBao(player, "Đã nhận phần thưởng nhiệm vụ câu cá mức " + QUEST_NAMES[difficulty] + ".");
        return true;
    }

    public boolean abandonQuest(Player player) {
        if (player == null) {
            return false;
        }
        resetQuestDay(player);
        ItemEvent data = player.itemEvent;
        if (!validDifficulty(data.fishingQuestDifficulty)) {
            Service.gI().sendThongBao(player, "Bạn không có nhiệm vụ câu cá để xóa.");
            return false;
        }
        String questName = QUEST_NAMES[data.fishingQuestDifficulty];
        data.fishingQuestDifficulty = -1;
        data.fishingQuestProgress = 0;
        Service.gI().sendThongBao(player, "Đã xóa nhiệm vụ mức " + questName
                + ". Lượt nhận nhiệm vụ hôm nay không được hoàn lại.");
        return true;
    }

    public boolean hasReachedDailyQuestLimit(Player player) {
        resetQuestDay(player);
        return player == null || player.itemEvent.fishingQuestsAcceptedToday >= MAX_DAILY_QUESTS;
    }

    public String questMenuText(Player player) {
        if (player == null) {
            return "Không thể đọc nhiệm vụ.";
        }
        resetQuestDay(player);
        ItemEvent data = player.itemEvent;
        if (!validDifficulty(data.fishingQuestDifficulty)) {
            return "Mỗi ngày được nhận tối đa " + MAX_DAILY_QUESTS
                    + " nhiệm vụ. Nhiệm vụ đã xóa vẫn tính một lượt; mức khó hơn yêu cầu cá hiếm hơn.\n"
                    + dailyQuestSummary(data) + ".";
        }
        byte difficulty = data.fishingQuestDifficulty;
        return "Nhiệm vụ " + QUEST_NAMES[difficulty] + "\n"
                + questObjective(difficulty) + "\nTiến độ: " + data.fishingQuestProgress + "/" + questTarget(difficulty)
                + "\n" + dailyQuestSummary(data)
                + (isActiveQuestComplete(player) ? "\nĐã hoàn thành, hãy nhận thưởng." : "");
    }

    private void updateQuestProgress(Player player, int fishTier) {
        resetQuestDay(player);
        ItemEvent data = player.itemEvent;
        byte difficulty = data.fishingQuestDifficulty;
        if (!validDifficulty(difficulty) || fishTier < QUEST_MIN_TIER[difficulty]
                || fishTier > QUEST_MAX_TIER[difficulty]) {
            return;
        }
        int target = questTarget(difficulty);
        int previous = data.fishingQuestProgress;
        data.fishingQuestProgress = Math.min(target, previous + 1);
        if (previous < target && data.fishingQuestProgress == target) {
            Service.gI().sendThongBao(player, "Nhiệm vụ câu cá mức " + QUEST_NAMES[difficulty]
                    + " đã hoàn thành. Hãy trở về gặp Ngư dân nhận thưởng.");
        }
    }

    private void resetQuestDay(Player player) {
        if (player == null) {
            return;
        }
        ItemEvent data = player.itemEvent;
        long now = System.currentTimeMillis();
        if (data.fishingQuestResetAt == 0) {
            data.fishingQuestResetAt = now;
        } else if (!Util.isSameDay(data.fishingQuestResetAt, now)) {
            data.fishingQuestResetAt = now;
            data.fishingQuestCompletedMask = 0;
            data.fishingQuestsAcceptedToday = 0;
            data.fishingQuestsCompletedToday = 0;
            data.fishingQuestDifficulty = -1;
            data.fishingQuestProgress = 0;
        }
    }

    private String dailyQuestSummary(ItemEvent data) {
        return "Đã nhận hôm nay: " + data.fishingQuestsAcceptedToday + "/" + MAX_DAILY_QUESTS
                + " • Hoàn thành: " + data.fishingQuestsCompletedToday;
    }

    private Map<Short, Integer> questRewards(byte difficulty) {
        Map<Short, Integer> rewards = new LinkedHashMap<>();
        switch (difficulty) {
            case QUEST_EASY -> {
                rewards.put(FishingItems.FISHER_COIN, 20);
                rewards.put(FishingItems.BAIT_WORM, 10);
            }
            case QUEST_MEDIUM -> {
                rewards.put(FishingItems.FISHER_COIN, 60);
                rewards.put(FishingItems.BAIT_SHRIMP, 5);
                rewards.put(FishingItems.FISHER_BADGE, 1);
            }
            case QUEST_HARD -> {
                rewards.put(FishingItems.FISHER_COIN, 150);
                rewards.put(FishingItems.BAIT_GOLDEN_CARP, 3);
                rewards.put(FishingItems.FISHER_BADGE, 2);
            }
            case QUEST_EXTREME -> {
                rewards.put(FishingItems.FISHER_COIN, 350);
                rewards.put(FishingItems.BAIT_GOLDEN_CARP, 5);
                rewards.put(FishingItems.FISHER_BADGE, 5);
                rewards.put(FishingItems.FISHER_CHEST, 1);
            }
            default -> {
            }
        }
        return rewards;
    }

    private int requiredEmptySlots(Player player, Map<Short, Integer> rewards) {
        int slots = 0;
        for (short itemId : rewards.keySet()) {
            Item existing = InventoryService.gI().findItemBag(player, itemId);
            if (existing == null || !existing.template.isUpToUp) {
                slots++;
            }
        }
        return slots;
    }

    private String completedDifficultyNames(int mask) {
        StringBuilder names = new StringBuilder();
        for (byte difficulty = 0; difficulty < QUEST_NAMES.length; difficulty++) {
            if ((mask & (1 << difficulty)) != 0) {
                if (!names.isEmpty()) {
                    names.append(", ");
                }
                names.append(QUEST_NAMES[difficulty]);
            }
        }
        return names.isEmpty() ? "chưa có" : names.toString();
    }

    private String questObjective(byte difficulty) {
        return "Câu " + questTarget(difficulty) + " cá cấp " + QUEST_MIN_TIER[difficulty]
                + "–" + QUEST_MAX_TIER[difficulty];
    }

    private int questTarget(byte difficulty) {
        return validDifficulty(difficulty) ? QUEST_TARGET[difficulty] : 0;
    }

    private boolean validDifficulty(byte difficulty) {
        return difficulty >= QUEST_EASY && difficulty <= QUEST_EXTREME;
    }

    private static byte bookRank(int tier) {
        if (tier <= 4) {
            return 0;
        }
        if (tier <= 8) {
            return 1;
        }
        if (tier <= 12) {
            return 2;
        }
        return 3;
    }

    private static final class FishBookEntry {

        private final String name;
        private final String rarity;
        private final short mobTemplateId;
        private final byte rank;

        private FishBookEntry(String name, String rarity, short mobTemplateId, byte rank) {
            this.name = name;
            this.rarity = rarity;
            this.mobTemplateId = mobTemplateId;
            this.rank = rank;
        }
    }
}
