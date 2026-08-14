package nro.models.admin;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import nro.models.data.LocalManager;
import nro.models.item.Item;
import nro.models.player.Player;
import nro.models.services.InventoryService;
import nro.models.services.ItemService;
import nro.models.services.RewardService;
import nro.models.services.Service;
import nro.models.utils.Logger;

/**
 * Data-driven random box/chest engine. The admin menu stores one JSON profile
 * per box template in gift_box_config; the runtime only needs to load profiles
 * once at startup and can then open every configured box through this class.
 */
public final class GiftBoxConfigService {

    private static final GiftBoxConfigService INSTANCE = new GiftBoxConfigService();
    private static final Gson GSON = new Gson();
    private final Map<Integer, GiftBoxConfig> configs = new ConcurrentHashMap<>();

    private GiftBoxConfigService() {
    }

    public static GiftBoxConfigService gI() {
        return INSTANCE;
    }

    public void load() {
        try (Connection connection = LocalManager.getConnection()) {
            load(connection);
        } catch (Exception e) {
            // The server must remain bootable before the optional migration is run.
            Logger.error("Could not load gift box configs (using legacy handlers): " + e.getMessage());
        }
    }

    public void load(Connection connection) {
        configs.clear();
        try {
            ensureSchema(connection);
            loadRows(connection);
            if (configs.isEmpty()) {
                seedBuiltIns(connection);
                loadRows(connection);
            }
            Logger.success(Logger.PURPLE + "Successfully loaded gift box configs (" + configs.size() + ")\n");
        } catch (Exception e) {
            // Reuse the caller's startup connection so a single-connection pool cannot deadlock.
            Logger.error("Could not load gift box configs (using legacy handlers): " + e.getMessage());
        }
    }

    private void ensureSchema(Connection connection) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("CREATE TABLE IF NOT EXISTS gift_box_config ("
                + "box_template_id INT NOT NULL PRIMARY KEY,"
                + "enabled TINYINT(1) NOT NULL DEFAULT 1,"
                + "min_empty_slots INT NOT NULL DEFAULT 1,"
                + "consume_quantity INT NOT NULL DEFAULT 1,"
                + "draw_count INT NOT NULL DEFAULT 1,"
                + "config_json TEXT NOT NULL,"
                + "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci")) {
            ps.executeUpdate();
        }
    }

    private void loadRows(Connection connection) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT box_template_id, enabled, min_empty_slots, consume_quantity, draw_count, config_json "
                + "FROM gift_box_config");
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                try {
                    GiftBoxConfig config = GSON.fromJson(rs.getString("config_json"), GiftBoxConfig.class);
                    if (config == null) {
                        continue;
                    }
                    config.boxTemplateId = rs.getInt("box_template_id");
                    config.enabled = rs.getBoolean("enabled");
                    config.minEmptySlots = Math.max(1, rs.getInt("min_empty_slots"));
                    config.consumeQuantity = Math.max(1, rs.getInt("consume_quantity"));
                    config.drawCount = Math.max(1, rs.getInt("draw_count"));
                    if (validate(config, false)) {
                        configs.put(config.boxTemplateId, config);
                    }
                } catch (JsonSyntaxException ignored) {
                    Logger.error("Invalid gift box JSON for template " + rs.getInt("box_template_id"));
                }
            }
        }
    }

    /** Returns true when the item was a configured box and the open was handled. */
    public boolean open(Player player, Item box) {
        if (player == null || box == null || box.template == null) {
            return false;
        }
        GiftBoxConfig config = configs.get((int) box.template.id);
        if (config == null) {
            return false;
        }
        if (!config.enabled) {
            Service.gI().sendThongBao(player, "Hộp quà này đang tạm khóa.");
            return true;
        }

        synchronized (player) {
            Item source = findBagItem(player, box.template.id);
            if (source == null || source.quantity < config.consumeQuantity) {
                Service.gI().sendThongBao(player, "Bạn không có đủ vật phẩm để mở hộp.");
                return true;
            }
            int requiredSlots = config.minEmptySlots + Math.max(0, config.drawCount - 1);
            if (InventoryService.gI().getCountEmptyBag(player) < requiredSlots) {
                Service.gI().sendThongBao(player, "Cần ít nhất " + requiredSlots + " ô trống trong hành trang.");
                return true;
            }
            if (config.rewards == null || config.rewards.isEmpty()) {
                Service.gI().sendThongBao(player, "Hộp quà này chưa có phần thưởng.");
                return true;
            }

            List<Item> generated = new ArrayList<>();
            for (int i = 0; i < config.drawCount; i++) {
                RewardConfig reward = chooseReward(config.rewards, player);
                if (reward == null) {
                    Service.gI().sendThongBao(player, "Cấu hình phần thưởng hộp không hợp lệ.");
                    return true;
                }
                Item item = createReward(player, reward);
                if (item == null) {
                    Service.gI().sendThongBao(player, "Không thể tạo vật phẩm phần thưởng.");
                    return true;
                }
                generated.add(item);
            }

            // Add first and only consume the box when every generated item fits.
            List<Item> added = new ArrayList<>();
            for (Item item : generated) {
                // addItemBag may consume the object by merging it into an
                // existing stack. Keep an independent snapshot for rollback.
                Item rollbackSnapshot = ItemService.gI().copyItem(item);
                if (!InventoryService.gI().addItemBag(player, item)) {
                    // Best-effort rollback for a failed multi-draw. The normal
                    // profiles draw once, but this keeps future profiles safe.
                    for (Item rollback : added) {
                        removeOne(player, rollback);
                    }
                    Service.gI().sendThongBao(player, "Hành trang không đủ chỗ cho phần thưởng.");
                    return true;
                }
                added.add(rollbackSnapshot);
            }
            InventoryService.gI().subQuantityItemsBag(player, source, config.consumeQuantity);
            StringBuilder rewardNames = new StringBuilder();
            for (Item reward : generated) {
                if (rewardNames.length() > 0) {
                    rewardNames.append(", ");
                }
                rewardNames.append(reward.template.name);
            }
            Service.gI().sendThongBao(player, "Bạn nhận được " + rewardNames + ".");
            return true;
        }
    }

    private Item findBagItem(Player player, int templateId) {
        for (Item item : player.inventory.itemsBag) {
            if (item != null && item.isNotNullItem() && item.template.id == templateId) {
                return item;
            }
        }
        return null;
    }

    private void removeOne(Player player, Item item) {
        for (Item current : player.inventory.itemsBag) {
            if (current != null && current.isNotNullItem() && current.template.id == item.template.id
                    && sameOptions(current, item)) {
                current.quantity -= item.quantity;
                if (current.quantity <= 0) {
                    player.inventory.itemsBag.set(player.inventory.itemsBag.indexOf(current),
                            ItemService.gI().createItemNull());
                }
                return;
            }
        }
    }

    private boolean sameOptions(Item first, Item second) {
        if (first.itemOptions.size() != second.itemOptions.size()) {
            return false;
        }
        for (int i = 0; i < first.itemOptions.size(); i++) {
            Item.ItemOption left = first.itemOptions.get(i);
            Item.ItemOption right = second.itemOptions.get(i);
            if (left == null || right == null || left.optionTemplate == null || right.optionTemplate == null
                    || left.optionTemplate.id != right.optionTemplate.id || left.param != right.param) {
                return false;
            }
        }
        return true;
    }

    private RewardConfig chooseReward(List<RewardConfig> rewards, Player player) {
        long total = 0;
        for (RewardConfig reward : rewards) {
            if (reward != null && reward.weight > 0 && matchesGender(reward, player)) {
                total += reward.weight;
            }
        }
        if (total <= 0) {
            return null;
        }
        long roll = ThreadLocalRandom.current().nextLong(total) + 1;
        long cursor = 0;
        for (RewardConfig reward : rewards) {
            if (reward == null || reward.weight <= 0 || !matchesGender(reward, player)) {
                continue;
            }
            cursor += reward.weight;
            if (roll <= cursor) {
                return reward;
            }
        }
        return null;
    }

    private boolean matchesGender(RewardConfig reward, Player player) {
        return reward.gender < 0 || reward.gender == 3 || reward.gender == player.gender;
    }

    private Item createReward(Player player, RewardConfig reward) {
        if (reward.itemId < 0 || reward.itemId >= nro.models.server.Manager.ITEM_TEMPLATES.size()) {
            return null;
        }
        Item item;
        try {
            item = ItemService.gI().createNewItem((short) reward.itemId, randomQuantity(reward.quantityMin, reward.quantityMax));
            if (reward.initBaseOptions) {
                RewardService.gI().initChiSoItem(item);
            }
            List<Item.ItemOption> options = new ArrayList<>();
            if (reward.initBaseOptions && item.itemOptions != null) {
                for (Item.ItemOption base : item.itemOptions) {
                    options.add(new Item.ItemOption(base));
                }
            }
            if (reward.options != null) {
                for (OptionConfig option : reward.options) {
                    if (option == null || option.id < 0 || option.id > 255) {
                        continue;
                    }
                    options.add(new Item.ItemOption(option.id, randomQuantity(option.paramMin, option.paramMax)));
                }
            }
            ExpiryConfig expiry = chooseExpiry(reward.expiry);
            if (expiry != null) {
                if ("permanent".equalsIgnoreCase(expiry.mode)) {
                    options.add(new Item.ItemOption(73, 0));
                } else if ("days".equalsIgnoreCase(expiry.mode)) {
                    options.add(new Item.ItemOption(93, randomQuantity(expiry.daysMin, expiry.daysMax)));
                }
            }
            item.itemOptions = ItemService.gI().mergeItemOptions((short) reward.itemId,
                    reward.useDefaultOptions, options);
            return item;
        } catch (RuntimeException e) {
            Logger.logException(GiftBoxConfigService.class, e, "Cannot create configured gift box reward");
            return null;
        }
    }

    private ExpiryConfig chooseExpiry(List<ExpiryConfig> expiry) {
        if (expiry == null || expiry.isEmpty()) {
            return null;
        }
        int total = 0;
        for (ExpiryConfig option : expiry) {
            if (option != null && option.weight > 0) {
                total += option.weight;
            }
        }
        if (total <= 0) {
            return null;
        }
        int roll = ThreadLocalRandom.current().nextInt(total) + 1;
        int cursor = 0;
        for (ExpiryConfig option : expiry) {
            if (option == null || option.weight <= 0) {
                continue;
            }
            cursor += option.weight;
            if (roll <= cursor) {
                return option;
            }
        }
        return null;
    }

    private int randomQuantity(int min, int max) {
        int low = Math.min(min, max);
        int high = Math.max(min, max);
        return low == high ? low : ThreadLocalRandom.current().nextInt(low, high + 1);
    }

    private boolean validate(GiftBoxConfig config, boolean strict) {
        if (config.boxTemplateId < 0 || config.rewards == null || config.rewards.isEmpty()) {
            return false;
        }
        long total = 0;
        for (RewardConfig reward : config.rewards) {
            if (reward == null || reward.itemId < 0 || reward.weight < 0
                    || reward.quantityMin <= 0 || reward.quantityMax <= 0) {
                return false;
            }
            total += reward.weight;
            if (reward.options != null) {
                for (OptionConfig option : reward.options) {
                    if (option == null || option.id < 0 || option.id > 255
                            || option.paramMin < Short.MIN_VALUE || option.paramMin > Short.MAX_VALUE
                            || option.paramMax < Short.MIN_VALUE || option.paramMax > Short.MAX_VALUE
                            || option.paramMin > option.paramMax) {
                        return false;
                    }
                }
            }
            if (reward.expiry != null) {
                for (ExpiryConfig expiry : reward.expiry) {
                    if (expiry == null || expiry.weight < 0) {
                        return false;
                    }
                    if ("days".equalsIgnoreCase(expiry.mode)
                            && (expiry.daysMin < 1 || expiry.daysMax < expiry.daysMin || expiry.daysMax > 3650)) {
                        return false;
                    }
                }
            }
        }
        return total > 0;
    }

    private void seedBuiltIns(Connection connection) throws SQLException {
        for (GiftBoxConfig config : builtIns()) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT IGNORE INTO gift_box_config (box_template_id, enabled, min_empty_slots, consume_quantity, draw_count, config_json) VALUES (?,1,?,?,?,?)")) {
                ps.setInt(1, config.boxTemplateId);
                ps.setInt(2, config.minEmptySlots);
                ps.setInt(3, config.consumeQuantity);
                ps.setInt(4, config.drawCount);
                ps.setString(5, GSON.toJson(config));
                ps.executeUpdate();
            }
        }
    }

    private List<GiftBoxConfig> builtIns() {
        List<GiftBoxConfig> result = new ArrayList<>();
        result.add(box(1776, 2, 10, new int[]{1772, 1773}, 14, 10));
        result.add(box(1777, 2, 25, new int[]{1761, 1731, 1732}, 236, 10));
        result.add(box(1591, 2, 20, new int[]{1588, 1589, 1595, 1587, 1593, 1590}, 210, 2));
        result.add(box(1594, 2, 20, new int[]{1588, 1589, 1595, 1587, 1593, 1590}, 210, 2));
        result.add(box(1592, 2, 25, new int[]{1588, 1589, 1595, 1587, 1593, 1590}, 210, 4));
        result.add(box(1840, 5, 27, new int[]{1588, 1589, 1595, 1587, 1593, 1590}, 210, 4));
        result.add(box(1755, 2, 20, new int[]{1741, 1742, 1743, 1744, 1745, 1746}, 210, 2));
        result.add(box(1756, 2, 20, new int[]{1741, 1742, 1743, 1744, 1745, 1746}, 210, 2));
        result.add(box(1757, 2, 27, new int[]{1741, 1742, 1743, 1744, 1745, 1746}, 210, 4));
        return result;
    }

    private GiftBoxConfig box(int id, int minSlots, int stat, int[] itemIds, int luckOption, int luckParam) {
        GiftBoxConfig config = new GiftBoxConfig();
        config.boxTemplateId = id;
        config.enabled = true;
        config.minEmptySlots = minSlots;
        config.consumeQuantity = 1;
        config.drawCount = 1;
        config.rewards = new ArrayList<>();
        for (int itemId : itemIds) {
            RewardConfig reward = new RewardConfig();
            reward.itemId = itemId;
            reward.weight = 1;
            reward.quantityMin = 1;
            reward.quantityMax = 1;
            reward.gender = 3;
            reward.initBaseOptions = true;
            reward.useDefaultOptions = false;
            reward.options = new ArrayList<>();
            reward.options.add(new OptionConfig(50, stat, stat));
            reward.options.add(new OptionConfig(77, stat, stat));
            reward.options.add(new OptionConfig(103, stat, stat));
            reward.options.add(new OptionConfig(luckOption, luckParam, luckParam));
            reward.expiry = new ArrayList<>();
            reward.expiry.add(new ExpiryConfig("permanent", 1, 0, 0));
            reward.expiry.add(new ExpiryConfig("days", 99, 3, 3));
            config.rewards.add(reward);
        }
        return config;
    }

    public static class GiftBoxConfig {
        public int boxTemplateId;
        public boolean enabled = true;
        public int minEmptySlots = 1;
        public int consumeQuantity = 1;
        public int drawCount = 1;
        public List<RewardConfig> rewards = new ArrayList<>();
    }

    public static class RewardConfig {
        public int itemId;
        public int weight = 1;
        public int quantityMin = 1;
        public int quantityMax = 1;
        public int gender = 3;
        public boolean initBaseOptions;
        public boolean useDefaultOptions;
        public List<OptionConfig> options = new ArrayList<>();
        public List<ExpiryConfig> expiry = new ArrayList<>();
    }

    public static class OptionConfig {
        public int id;
        public int paramMin;
        public int paramMax;

        public OptionConfig() {
        }

        public OptionConfig(int id, int paramMin, int paramMax) {
            this.id = id;
            this.paramMin = paramMin;
            this.paramMax = paramMax;
        }
    }

    public static class ExpiryConfig {
        public String mode;
        public int weight;
        public int daysMin;
        public int daysMax;

        public ExpiryConfig() {
        }

        public ExpiryConfig(String mode, int weight, int daysMin, int daysMax) {
            this.mode = mode;
            this.weight = weight;
            this.daysMin = daysMin;
            this.daysMax = daysMax;
        }
    }
}
