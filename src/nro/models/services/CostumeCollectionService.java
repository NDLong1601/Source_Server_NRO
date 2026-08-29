package nro.models.services;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import nro.models.data.LocalManager;
import nro.models.item.Item;
import nro.models.network.Message;
import nro.models.player.Player;
import nro.models.player.PlayerConfig;
import nro.models.player_system.Template;
import nro.models.server.Manager;
import nro.models.shop.ItemShop;
import nro.models.shop.Shop;
import nro.models.shop.TabShop;
import nro.models.utils.Logger;

/**
 * Persistent collection of every costume a player has owned.
 *
 * The collection is intentionally stored in a normalized table instead of the
 * player's inventory JSON. That keeps an unlock after the physical item is
 * sold or deleted and avoids growing the main player row.
 */
public class CostumeCollectionService {

    public static final byte ACTION_COLLECTION = 44;
    public static final byte ACTION_COLLECTION_ACHIEVEMENTS = 45;
    public static final byte ACTION_CLAIM_COLLECTION_ACHIEVEMENT = 46;
    private static final byte COSTUME_TYPE = 5;
    private static final int CHUNK_SIZE = 50;
    private static final byte ACHIEVEMENT_BY_COUNT = 0;
    private static final byte ACHIEVEMENT_BY_THEME = 1;
    private static final byte ACHIEVEMENT_DETAIL_VERSION = 1;

    private static CostumeCollectionService instance;
    private volatile boolean schemaReady;

    public static CostumeCollectionService gI() {
        if (instance == null) {
            instance = new CostumeCollectionService();
        }
        return instance;
    }

    public void recordOwnership(Player player, Item item) {
        if (!isPlayerCostume(player, item)) {
            return;
        }
        try {
            ensureSchema();
            LocalManager.executeUpdate(
                    "INSERT IGNORE INTO player_costume_collection "
                    + "(player_id, item_template_id) VALUES (?, ?)",
                    player.id, item.template.id);
        } catch (Exception ex) {
            Logger.logException(CostumeCollectionService.class, ex,
                    "Không thể ghi nhận cải trang " + item.template.id + " của player " + player.id);
        }
    }

    public void sendCollection(Player player) {
        if (player == null || !player.isPl()) {
            return;
        }
        try {
            ensureSchema();
            backfillCurrentInventory(player);

            Set<Short> ownedIds = loadOwnedIds(player.id);
            Map<Short, Item> currentItems = findCurrentCostumes(player);
            short equippedId = getEquippedCostumeId(player);
            List<CostumeGroup> costumes = getCostumesForGender(player.gender);

            int total = costumes.size();
            int chunkCount = Math.max(1, (total + CHUNK_SIZE - 1) / CHUNK_SIZE);
            for (int chunkIndex = 0; chunkIndex < chunkCount; chunkIndex++) {
                int from = chunkIndex * CHUNK_SIZE;
                int to = Math.min(total, from + CHUNK_SIZE);
                Message message = new Message(127);
                try {
                    message.writer().writeByte(ACTION_COLLECTION);
                    message.writer().writeBoolean(chunkIndex == 0);
                    message.writer().writeBoolean(chunkIndex == chunkCount - 1);
                    message.writer().writeShort(to - from);
                    for (int index = from; index < to; index++) {
                        writeCostume(message, costumes.get(index), ownedIds, currentItems, equippedId);
                    }
                    player.sendMessage(message);
                } finally {
                    message.cleanup();
                }
            }
            sendCollectionAchievements(player, ownedIds, costumes);
        } catch (Exception ex) {
            Logger.logException(CostumeCollectionService.class, ex,
                    "Không thể gửi bộ sưu tập cải trang cho player " + player.id);
            Service.gI().sendThongBao(player, "Không thể tải bộ sưu tập cải trang");
        }
    }

    private void writeCostume(Message message, CostumeGroup group, Set<Short> ownedIds,
            Map<Short, Item> currentItems, short equippedId) throws Exception {
        Template.ItemTemplate template = group.canonical;
        Item currentItem = currentItems.get(template.id);
        ShopLocation shopLocation = findShopLocation(template.id);
        List<Item.ItemOption> options = resolveOptions(template.id, currentItem, shopLocation);

        message.writer().writeShort(template.id);
        message.writer().writeShort(template.iconID);
        message.writer().writeUTF(safe(template.name));
        message.writer().writeUTF(safe(template.description));
        message.writer().writeBoolean(group.hasOwnedTemplate(ownedIds));
        message.writer().writeBoolean(group.containsTemplate(equippedId));
        message.writer().writeByte(template.level);
        message.writer().writeShort(template.head);
        message.writer().writeShort(template.body);
        message.writer().writeShort(template.leg);
        message.writer().writeUTF(shopLocation.saleText);

        int optionCount = Math.min(255, options.size());
        message.writer().writeByte(optionCount);
        for (int i = 0; i < optionCount; i++) {
            Item.ItemOption option = options.get(i);
            message.writer().writeUTF(option == null || option.optionTemplate == null
                    ? "" : safe(option.getOptionString()));
        }
    }

    private synchronized void ensureSchema() throws Exception {
        if (schemaReady) {
            return;
        }
        LocalManager.executeUpdate(
                "CREATE TABLE IF NOT EXISTS player_costume_collection ("
                + "player_id INT NOT NULL,"
                + "item_template_id SMALLINT NOT NULL,"
                + "acquired_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                + "PRIMARY KEY (player_id, item_template_id),"
                + "KEY idx_costume_template (item_template_id)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci");
        LocalManager.executeUpdate(
                "CREATE TABLE IF NOT EXISTS costume_collection_achievement ("
                + "id SMALLINT NOT NULL,"
                + "name VARCHAR(100) NOT NULL,"
                + "description VARCHAR(255) NOT NULL,"
                + "condition_type TINYINT NOT NULL DEFAULT 0,"
                + "required_count SMALLINT NOT NULL DEFAULT 0,"
                + "required_template_ids VARCHAR(255) NOT NULL DEFAULT '',"
                + "reward_gold BIGINT NOT NULL DEFAULT 0,"
                + "reward_gem INT NOT NULL DEFAULT 0,"
                + "PRIMARY KEY (id)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci");
        LocalManager.executeUpdate(
                "CREATE TABLE IF NOT EXISTS costume_collection_achievement_reward ("
                + "achievement_id SMALLINT NOT NULL,"
                + "item_template_id SMALLINT NOT NULL,"
                + "quantity INT NOT NULL DEFAULT 1,"
                + "PRIMARY KEY (achievement_id, item_template_id),"
                + "KEY idx_costume_collection_reward_item (item_template_id)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci");
        LocalManager.executeUpdate(
                "CREATE TABLE IF NOT EXISTS player_costume_collection_achievement ("
                + "player_id INT NOT NULL,"
                + "achievement_id SMALLINT NOT NULL,"
                + "claimed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                + "PRIMARY KEY (player_id, achievement_id)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci");
        seedDefaultAchievements();
        schemaReady = true;
    }

    /**
     * Default data is intentionally inserted with INSERT IGNORE. Operators can
     * edit the rows in costume_collection_achievement without a code deploy.
     */
    private void seedDefaultAchievements() throws Exception {
        LocalManager.executeUpdate(
                "INSERT IGNORE INTO costume_collection_achievement "
                + "(id, name, description, condition_type, required_count, required_template_ids, reward_gold, reward_gem) VALUES "
                + "(1, 'Nhà sưu tầm tập sự', 'Sở hữu 10 cải trang khác nhau.', 0, 10, '', 500000, 25),"
                + "(2, 'Nhà sưu tầm kỳ cựu', 'Sở hữu 30 cải trang khác nhau.', 0, 30, '', 1500000, 50),"
                + "(3, 'Bậc thầy cải trang', 'Sở hữu 75 cải trang khác nhau.', 0, 75, '', 5000000, 100),"
                + "(10, 'Bộ Hải Tặc', 'Hoàn thành bộ sưu tập Hải Tặc.', 1, 9, '2001,2002,2003,2004,2005,2006,2007,2008,2009', 2000000, 75)");
    }

    private void backfillCurrentInventory(Player player) throws Exception {
        Set<Short> ids = new LinkedHashSet<>();
        collectCostumeIds(player.inventory.itemsBody, ids);
        collectCostumeIds(player.inventory.itemsBag, ids);
        collectCostumeIds(player.inventory.itemsBox, ids);
        if (ids.isEmpty()) {
            return;
        }
        try (Connection connection = LocalManager.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT IGNORE INTO player_costume_collection "
                        + "(player_id, item_template_id) VALUES (?, ?)")) {
            for (short id : ids) {
                statement.setLong(1, player.id);
                statement.setShort(2, id);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private Set<Short> loadOwnedIds(long playerId) throws Exception {
        Set<Short> result = new HashSet<>();
        try (Connection connection = LocalManager.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT item_template_id FROM player_costume_collection WHERE player_id = ?")) {
            statement.setLong(1, playerId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    result.add(resultSet.getShort(1));
                }
            }
        }
        return result;
    }

    public synchronized void claimCollectionAchievement(Player player, short achievementId) {
        if (player == null || !player.isPl()) {
            return;
        }
        try {
            ensureSchema();
            backfillCurrentInventory(player);
            CollectionAchievement achievement = findAchievement(achievementId);
            if (achievement == null) {
                Service.gI().sendThongBao(player, "Thành tựu sưu tập không tồn tại");
                return;
            }

            Set<Short> ownedIds = loadOwnedIds(player.id);
            List<CostumeGroup> costumes = getCostumesForGender(player.gender);
            if (!isAchievementComplete(achievement, ownedIds, costumes)) {
                Service.gI().sendThongBao(player, "Bạn chưa hoàn thành thành tựu này");
                sendCollectionAchievements(player, ownedIds, costumes);
                return;
            }
            if (!canReceiveItemRewards(player, achievement)) {
                Service.gI().sendThongBao(player, "Hành trang không đủ chỗ để nhận phần thưởng vật phẩm");
                sendCollectionAchievements(player, ownedIds, costumes);
                return;
            }
            if (!markAchievementClaimed(player.id, achievement.id)) {
                Service.gI().sendThongBao(player, "Phần thưởng này đã được nhận");
                sendCollectionAchievements(player, ownedIds, costumes);
                return;
            }

            long goldSpace = Math.max(0L, PlayerConfig.getMaxGold() - player.inventory.gold);
            player.inventory.gold += Math.min(goldSpace, achievement.rewardGold);
            int gemSpace = Math.max(0, PlayerConfig.getMaxGem() - player.inventory.gem);
            player.inventory.gem += Math.min(gemSpace, achievement.rewardGem);
            grantItemRewards(player, achievement);
            Service.gI().sendMoney(player);
            InventoryService.gI().sendItemBags(player);
            Service.gI().sendThongBao(player, "Đã nhận thưởng " + achievement.name + ": "
                    + achievement.getRewardSummary() + ".");
            sendCollectionAchievements(player, ownedIds, costumes);
        } catch (Exception ex) {
            Logger.logException(CostumeCollectionService.class, ex,
                    "Không thể nhận thành tựu sưu tập " + achievementId + " của player " + player.id);
            Service.gI().sendThongBao(player, "Không thể nhận phần thưởng sưu tập");
        }
    }

    private void sendCollectionAchievements(Player player, Set<Short> ownedIds,
            List<CostumeGroup> costumes) throws Exception {
        List<CollectionAchievement> achievements = loadAchievements();
        Set<Short> claimedIds = loadClaimedAchievementIds(player.id);
        Message message = new Message(127);
        try {
            message.writer().writeByte(ACTION_COLLECTION_ACHIEVEMENTS);
            message.writer().writeShort(countOwnedGroups(ownedIds, costumes));
            message.writer().writeShort(costumes.size());
            message.writer().writeByte(Math.min(255, achievements.size()));
            for (int index = 0; index < achievements.size() && index < 255; index++) {
                CollectionAchievement achievement = achievements.get(index);
                int progress = getAchievementProgress(achievement, ownedIds, costumes);
                int target = achievement.getTarget();
                message.writer().writeShort(achievement.id);
                message.writer().writeUTF(safe(achievement.name));
                message.writer().writeUTF(achievement.getDescriptionWithRewardItems());
                message.writer().writeShort(Math.min(Short.MAX_VALUE, progress));
                message.writer().writeShort(Math.min(Short.MAX_VALUE, target));
                message.writer().writeLong(achievement.rewardGold);
                message.writer().writeInt(achievement.rewardGem);
                message.writer().writeBoolean(progress >= target);
                message.writer().writeBoolean(claimedIds.contains(achievement.id));
            }
            writeAchievementDetails(message, achievements, ownedIds, costumes);
            player.sendMessage(message);
        } finally {
            message.cleanup();
        }
    }

    /**
     * Appended after the legacy achievement rows so older clients can ignore
     * it while newer clients can render complete rewards and theme targets.
     */
    private void writeAchievementDetails(Message message, List<CollectionAchievement> achievements,
            Set<Short> ownedIds, List<CostumeGroup> costumes) throws Exception {
        int achievementCount = Math.min(255, achievements.size());
        message.writer().writeByte(ACHIEVEMENT_DETAIL_VERSION);
        message.writer().writeByte(achievementCount);
        for (int index = 0; index < achievementCount; index++) {
            CollectionAchievement achievement = achievements.get(index);
            message.writer().writeShort(achievement.id);
            message.writer().writeUTF(safe(achievement.description));
            message.writer().writeByte(achievement.conditionType);

            int rewardCount = Math.min(255, achievement.itemRewards.size());
            message.writer().writeByte(rewardCount);
            for (int rewardIndex = 0; rewardIndex < rewardCount; rewardIndex++) {
                CollectionItemReward reward = achievement.itemRewards.get(rewardIndex);
                Template.ItemTemplate template = getItemTemplate(reward.itemTemplateId);
                message.writer().writeShort(reward.itemTemplateId);
                message.writer().writeShort(template == null ? -1 : template.iconID);
                message.writer().writeInt(reward.quantity);
                message.writer().writeUTF(template == null
                        ? "Vật phẩm " + reward.itemTemplateId : safe(template.name));
            }

            short[] requiredIds = achievement.conditionType == ACHIEVEMENT_BY_THEME
                    ? achievement.getRequiredTemplateIds() : new short[0];
            int requiredCount = Math.min(255, requiredIds.length);
            message.writer().writeByte(requiredCount);
            for (int requiredIndex = 0; requiredIndex < requiredCount; requiredIndex++) {
                short templateId = requiredIds[requiredIndex];
                Template.ItemTemplate template = getItemTemplate(templateId);
                CostumeGroup group = findCostumeGroup(costumes, templateId);
                boolean owned = group == null ? ownedIds.contains(templateId) : group.hasOwnedTemplate(ownedIds);
                message.writer().writeShort(templateId);
                message.writer().writeShort(template == null ? -1 : template.iconID);
                message.writer().writeUTF(template == null
                        ? "Cải trang " + templateId : safe(template.name));
                message.writer().writeBoolean(owned);
            }
        }
    }

    private CostumeGroup findCostumeGroup(List<CostumeGroup> costumes, short templateId) {
        for (CostumeGroup group : costumes) {
            if (group.containsTemplate(templateId)) {
                return group;
            }
        }
        return null;
    }

    private List<CollectionAchievement> loadAchievements() throws Exception {
        List<CollectionAchievement> result = new ArrayList<>();
        try (Connection connection = LocalManager.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT a.id, a.name, a.description, a.condition_type, a.required_count, "
                        + "a.required_template_ids, a.reward_gold, a.reward_gem, "
                        + "COALESCE((SELECT GROUP_CONCAT(CONCAT(r.item_template_id, ':', r.quantity) "
                        + "ORDER BY r.item_template_id SEPARATOR ',') "
                        + "FROM costume_collection_achievement_reward r "
                        + "WHERE r.achievement_id = a.id), '') AS reward_items "
                        + "FROM costume_collection_achievement a ORDER BY a.id");
                ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                result.add(new CollectionAchievement(
                        resultSet.getShort("id"),
                        resultSet.getString("name"),
                        resultSet.getString("description"),
                        resultSet.getByte("condition_type"),
                        resultSet.getInt("required_count"),
                        resultSet.getString("required_template_ids"),
                        Math.max(0L, resultSet.getLong("reward_gold")),
                        Math.max(0, resultSet.getInt("reward_gem")),
                        resultSet.getString("reward_items")));
            }
        }
        return result;
    }

    private CollectionAchievement findAchievement(short id) throws Exception {
        for (CollectionAchievement achievement : loadAchievements()) {
            if (achievement.id == id) {
                return achievement;
            }
        }
        return null;
    }

    private Set<Short> loadClaimedAchievementIds(long playerId) throws Exception {
        Set<Short> result = new HashSet<>();
        try (Connection connection = LocalManager.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT achievement_id FROM player_costume_collection_achievement WHERE player_id = ?")) {
            statement.setLong(1, playerId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    result.add(resultSet.getShort(1));
                }
            }
        }
        return result;
    }

    private boolean markAchievementClaimed(long playerId, short achievementId) throws Exception {
        try (Connection connection = LocalManager.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT IGNORE INTO player_costume_collection_achievement "
                        + "(player_id, achievement_id) VALUES (?, ?)")) {
            statement.setLong(1, playerId);
            statement.setShort(2, achievementId);
            return statement.executeUpdate() > 0;
        }
    }

    private boolean canReceiveItemRewards(Player player, CollectionAchievement achievement) {
        int neededBagSlots = 0;
        int addedBagSlots = 0;
        long predictedGold = Math.min(PlayerConfig.getMaxGold(), player.inventory.gold + achievement.rewardGold);
        long predictedGem = Math.min(Integer.MAX_VALUE, (long) player.inventory.gem + achievement.rewardGem);
        long predictedRuby = player.inventory.ruby;
        for (CollectionItemReward reward : achievement.itemRewards) {
            Template.ItemTemplate template = getItemTemplate(reward.itemTemplateId);
            if (template == null) {
                return false;
            }
            if (reward.itemTemplateId == 517) {
                addedBagSlots += reward.quantity;
                if ((long) player.inventory.itemsBag.size() + addedBagSlots > PlayerConfig.getMaxBagSlots()) {
                    return false;
                }
                continue;
            }
            if (reward.itemTemplateId == 518) {
                if ((long) player.inventory.itemsBox.size() + reward.quantity > PlayerConfig.getMaxBoxSlots()) {
                    return false;
                }
                continue;
            }
            switch (template.type) {
                case 9:
                    predictedGold += reward.quantity;
                    if (predictedGold > PlayerConfig.getMaxGold()) {
                        return false;
                    }
                    break;
                case 10:
                    predictedGem += reward.quantity;
                    if (predictedGem > Integer.MAX_VALUE) {
                        return false;
                    }
                    break;
                case 34:
                    predictedRuby += reward.quantity;
                    if (predictedRuby > Integer.MAX_VALUE) {
                        return false;
                    }
                    break;
                default:
                    neededBagSlots++;
                    break;
            }
        }
        return neededBagSlots <= InventoryService.gI().getCountEmptyBag(player) + addedBagSlots;
    }

    private void grantItemRewards(Player player, CollectionAchievement achievement) {
        for (CollectionItemReward reward : achievement.itemRewards) {
            if (reward.itemTemplateId == 517 || reward.itemTemplateId == 518) {
                for (int count = 0; count < reward.quantity; count++) {
                    Item expansion = ItemService.gI().createNewItemWithDefaultOptions(reward.itemTemplateId, 1);
                    InventoryService.gI().addItemBag(player, expansion);
                }
                continue;
            }
            Item item = ItemService.gI().createNewItemWithDefaultOptions(reward.itemTemplateId, reward.quantity);
            if (item != null) {
                InventoryService.gI().addItemBag(player, item);
            }
        }
    }

    private Template.ItemTemplate getItemTemplate(short itemTemplateId) {
        return itemTemplateId >= 0 && itemTemplateId < Manager.ITEM_TEMPLATES.size()
                ? Manager.ITEM_TEMPLATES.get(itemTemplateId) : null;
    }

    private int countOwnedGroups(Set<Short> ownedIds, List<CostumeGroup> costumes) {
        int total = 0;
        for (CostumeGroup group : costumes) {
            if (group.hasOwnedTemplate(ownedIds)) {
                total++;
            }
        }
        return total;
    }

    private boolean isAchievementComplete(CollectionAchievement achievement, Set<Short> ownedIds,
            List<CostumeGroup> costumes) {
        return getAchievementProgress(achievement, ownedIds, costumes) >= achievement.getTarget();
    }

    private int getAchievementProgress(CollectionAchievement achievement, Set<Short> ownedIds,
            List<CostumeGroup> costumes) {
        if (achievement.conditionType == ACHIEVEMENT_BY_COUNT) {
            return countOwnedGroups(ownedIds, costumes);
        }
        if (achievement.conditionType == ACHIEVEMENT_BY_THEME) {
            int complete = 0;
            for (short templateId : achievement.getRequiredTemplateIds()) {
                for (CostumeGroup group : costumes) {
                    if (group.containsTemplate(templateId) && group.hasOwnedTemplate(ownedIds)) {
                        complete++;
                        break;
                    }
                }
            }
            return complete;
        }
        return 0;
    }

    private List<CostumeGroup> getCostumesForGender(byte gender) {
        Map<String, CostumeGroup> groupedCostumes = new LinkedHashMap<>();
        for (Template.ItemTemplate template : Manager.ITEM_TEMPLATES) {
            if (template != null && template.type == COSTUME_TYPE
                    && (template.gender == gender || template.gender > 2)
                    && !isHeadOnlyCostume(template)) {
                String appearanceKey = getAppearanceKey(template);
                CostumeGroup group = groupedCostumes.get(appearanceKey);
                CostumeQuality quality = evaluateCostumeQuality(template, gender);
                if (group == null) {
                    group = new CostumeGroup(template, quality);
                    groupedCostumes.put(appearanceKey, group);
                } else {
                    group.add(template, quality);
                }
            }
        }
        return new ArrayList<>(groupedCostumes.values());
    }

    private boolean isHeadOnlyCostume(Template.ItemTemplate template) {
        return template.head >= 0 && template.body < 0 && template.leg < 0;
    }

    private String getAppearanceKey(Template.ItemTemplate template) {
        return template.iconID + ":" + template.head + ":" + template.body + ":" + template.leg;
    }

    private CostumeQuality evaluateCostumeQuality(Template.ItemTemplate template, byte playerGender) {
        ShopLocation location = findShopLocation(template.id);
        int optionCount = resolveOptions(template.id, null, location).size();
        return new CostumeQuality(
                location.firstItemShop != null,
                optionCount,
                template.gender == playerGender,
                !safe(template.description).trim().isEmpty());
    }

    private Map<Short, Item> findCurrentCostumes(Player player) {
        Map<Short, Item> result = new HashMap<>();
        putCurrentCostumes(player.inventory.itemsBody, result);
        putCurrentCostumes(player.inventory.itemsBag, result);
        putCurrentCostumes(player.inventory.itemsBox, result);
        return result;
    }

    private short getEquippedCostumeId(Player player) {
        if (player.inventory.itemsBody.size() <= COSTUME_TYPE) {
            return -1;
        }
        Item item = player.inventory.itemsBody.get(COSTUME_TYPE);
        return isCostume(item) ? item.template.id : -1;
    }

    private ShopLocation findShopLocation(short templateId) {
        ShopLocation location = new ShopLocation();
        LinkedHashSet<String> salePlaces = new LinkedHashSet<>();
        for (Shop shop : Manager.SHOPS) {
            if (shop == null || shop.tabShops == null) {
                continue;
            }
            Template.NpcTemplate npc = Manager.getNpcTemplate(shop.npcId);
            String npcName = npc == null ? "NPC " + shop.npcId : npc.name;
            for (TabShop tab : shop.tabShops) {
                if (tab == null || tab.itemShops == null) {
                    continue;
                }
                for (ItemShop itemShop : tab.itemShops) {
                    if (itemShop != null && itemShop.temp != null && itemShop.temp.id == templateId) {
                        if (location.firstItemShop == null) {
                            location.firstItemShop = itemShop;
                        }
                        String tabName = safe(tab.name).replace('\n', ' ').trim();
                        salePlaces.add("Shop " + (tabName.isEmpty() ? safe(shop.tagName) : tabName)
                                + " - NPC " + npcName);
                    }
                }
            }
        }
        location.saleText = salePlaces.isEmpty()
                ? "Chưa mở bán"
                : "Đang bán tại: " + String.join("; ", salePlaces);
        return location;
    }

    private List<Item.ItemOption> resolveOptions(short templateId, Item currentItem, ShopLocation location) {
        List<Item.ItemOption> result = new ArrayList<>();
        if (currentItem != null && currentItem.itemOptions != null && !currentItem.itemOptions.isEmpty()) {
            copyOptions(currentItem.itemOptions, result);
        } else if (location.firstItemShop != null && location.firstItemShop.options != null
                && !location.firstItemShop.options.isEmpty()) {
            copyOptions(location.firstItemShop.options, result);
        } else {
            result.addAll(ItemService.gI().getDefaultItemOptions(templateId));
        }
        return result;
    }

    private void copyOptions(List<Item.ItemOption> source, List<Item.ItemOption> target) {
        for (Item.ItemOption option : source) {
            if (option != null && option.optionTemplate != null) {
                target.add(new Item.ItemOption(option));
            }
        }
    }

    private void collectCostumeIds(List<Item> items, Set<Short> ids) {
        if (items == null) {
            return;
        }
        for (Item item : items) {
            if (isCostume(item)) {
                ids.add(item.template.id);
            }
        }
    }

    private void putCurrentCostumes(List<Item> items, Map<Short, Item> result) {
        if (items == null) {
            return;
        }
        for (Item item : items) {
            if (isCostume(item)) {
                result.putIfAbsent(item.template.id, item);
            }
        }
    }

    private boolean isPlayerCostume(Player player, Item item) {
        return player != null && player.isPl() && isCostume(item);
    }

    private boolean isCostume(Item item) {
        return item != null && item.isNotNullItem() && item.template.type == COSTUME_TYPE;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private static class ShopLocation {

        private ItemShop firstItemShop;
        private String saleText = "Chưa mở bán";
    }

    private static class CollectionAchievement {

        private final short id;
        private final String name;
        private final String description;
        private final byte conditionType;
        private final int requiredCount;
        private final String requiredTemplateIds;
        private final long rewardGold;
        private final int rewardGem;
        private final List<CollectionItemReward> itemRewards;

        private CollectionAchievement(short id, String name, String description, byte conditionType,
                int requiredCount, String requiredTemplateIds, long rewardGold, int rewardGem,
                String rewardItems) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.conditionType = conditionType;
            this.requiredCount = Math.max(0, requiredCount);
            this.requiredTemplateIds = requiredTemplateIds == null ? "" : requiredTemplateIds;
            this.rewardGold = rewardGold;
            this.rewardGem = rewardGem;
            this.itemRewards = parseItemRewards(rewardItems);
        }

        private List<CollectionItemReward> parseItemRewards(String value) {
            List<CollectionItemReward> result = new ArrayList<>();
            if (value == null || value.trim().isEmpty()) {
                return result;
            }
            for (String entry : value.split(",")) {
                String[] values = entry.split(":", 2);
                if (values.length != 2) {
                    continue;
                }
                try {
                    short itemTemplateId = Short.parseShort(values[0].trim());
                    int quantity = Integer.parseInt(values[1].trim());
                    if (itemTemplateId >= 0 && quantity > 0) {
                        result.add(new CollectionItemReward(itemTemplateId, quantity));
                    }
                } catch (NumberFormatException ignored) {
                    // Ignore stale or malformed reward data until it is corrected in admin.
                }
            }
            return result;
        }

        private String getDescriptionWithRewardItems() {
            if (itemRewards.isEmpty()) {
                return CostumeCollectionService.gI().safe(description);
            }
            String baseDescription = CostumeCollectionService.gI().safe(description);
            return baseDescription + (baseDescription.isEmpty() ? "" : "\n")
                    + "Thưởng vật phẩm: " + getItemRewardSummary();
        }

        private String getRewardSummary() {
            List<String> parts = new ArrayList<>();
            if (rewardGold > 0) {
                parts.add(rewardGold + " vàng");
            }
            if (rewardGem > 0) {
                parts.add(rewardGem + " ngọc");
            }
            if (!itemRewards.isEmpty()) {
                parts.add("vật phẩm: " + getItemRewardSummary());
            }
            return parts.isEmpty() ? "không có vật phẩm" : String.join(", ", parts);
        }

        private String getItemRewardSummary() {
            StringBuilder result = new StringBuilder();
            int visibleCount = Math.min(4, itemRewards.size());
            for (int index = 0; index < visibleCount; index++) {
                if (index > 0) {
                    result.append(", ");
                }
                CollectionItemReward reward = itemRewards.get(index);
                Template.ItemTemplate template = CostumeCollectionService.gI().getItemTemplate(reward.itemTemplateId);
                String itemName = template == null ? "Vật phẩm " + reward.itemTemplateId
                        : CostumeCollectionService.gI().safe(template.name);
                result.append(itemName).append(" x").append(reward.quantity);
            }
            if (itemRewards.size() > visibleCount) {
                result.append(" và ").append(itemRewards.size() - visibleCount).append(" vật phẩm khác");
            }
            return result.toString();
        }

        private short[] getRequiredTemplateIds() {
            String[] values = requiredTemplateIds.split(",");
            List<Short> ids = new ArrayList<>();
            for (String value : values) {
                try {
                    String trimmed = value.trim();
                    if (!trimmed.isEmpty()) {
                        ids.add(Short.parseShort(trimmed));
                    }
                } catch (NumberFormatException ignored) {
                    // Invalid configured IDs are simply ignored until corrected.
                }
            }
            short[] result = new short[ids.size()];
            for (int index = 0; index < ids.size(); index++) {
                result[index] = ids.get(index);
            }
            return result;
        }

        private int getTarget() {
            if (conditionType == ACHIEVEMENT_BY_THEME) {
                int configuredSize = getRequiredTemplateIds().length;
                return configuredSize > 0 ? configuredSize : requiredCount;
            }
            return requiredCount;
        }
    }

    private static class CollectionItemReward {

        private final short itemTemplateId;
        private final int quantity;

        private CollectionItemReward(short itemTemplateId, int quantity) {
            this.itemTemplateId = itemTemplateId;
            this.quantity = quantity;
        }
    }

    private static class CostumeQuality {

        private final boolean hasShop;
        private final int optionCount;
        private final boolean exactGender;
        private final boolean hasDescription;

        private CostumeQuality(boolean hasShop, int optionCount, boolean exactGender, boolean hasDescription) {
            this.hasShop = hasShop;
            this.optionCount = optionCount;
            this.exactGender = exactGender;
            this.hasDescription = hasDescription;
        }

        private int dataKinds() {
            return (hasShop ? 1 : 0) + (optionCount > 0 ? 1 : 0);
        }

        private boolean isBetterThan(CostumeQuality other) {
            if (dataKinds() != other.dataKinds()) {
                return dataKinds() > other.dataKinds();
            }
            if (optionCount != other.optionCount) {
                return optionCount > other.optionCount;
            }
            if (hasShop != other.hasShop) {
                return hasShop;
            }
            if (exactGender != other.exactGender) {
                return exactGender;
            }
            return hasDescription && !other.hasDescription;
        }
    }

    private static class CostumeGroup {

        private Template.ItemTemplate canonical;
        private CostumeQuality quality;
        private final List<Template.ItemTemplate> members = new ArrayList<>();

        private CostumeGroup(Template.ItemTemplate template, CostumeQuality quality) {
            canonical = template;
            this.quality = quality;
            members.add(template);
        }

        private void add(Template.ItemTemplate template, CostumeQuality candidateQuality) {
            members.add(template);
            if (candidateQuality.isBetterThan(quality)) {
                canonical = template;
                quality = candidateQuality;
            }
        }

        private boolean hasOwnedTemplate(Set<Short> ownedIds) {
            for (Template.ItemTemplate member : members) {
                if (ownedIds.contains(member.id)) {
                    return true;
                }
            }
            return false;
        }

        private boolean containsTemplate(short templateId) {
            for (Template.ItemTemplate member : members) {
                if (member.id == templateId) {
                    return true;
                }
            }
            return false;
        }
    }
}
