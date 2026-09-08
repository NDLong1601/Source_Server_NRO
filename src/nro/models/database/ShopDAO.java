package nro.models.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import nro.models.item.Item;
import nro.models.player_system.Template.ItemOptionTemplate;
import nro.models.player_system.Template.ItemTemplate;
import nro.models.server.GameRuntime;
import nro.models.shop.ItemShop;
import nro.models.shop.Shop;
import nro.models.shop.TabShop;
import nro.models.utils.Logger;

public final class ShopDAO {

    private ShopDAO() {
    }

    /** Compatibility entry point for runtime refreshes after bootstrap. */
    public static List<Shop> getShops(Connection connection) {
        try {
            return getShops(connection, GameRuntime.gI().templates().itemTemplates(),
                    GameRuntime.gI().templates().itemOptionTemplates(),
                    GameRuntime.gI().templates().defaultItemOptions());
        } catch (SQLException | IllegalArgumentException error) {
            Logger.logException(ShopDAO.class, error, "Cannot load shop snapshot");
            return new ArrayList<>();
        }
    }

    /** Strict bootstrap path: any bad row rejects the complete candidate snapshot. */
    public static List<Shop> getShops(Connection connection, List<ItemTemplate> itemTemplates,
            List<ItemOptionTemplate> optionTemplates,
            Map<Short, List<Item.ItemOption>> defaultOptions) throws SQLException {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(itemTemplates, "itemTemplates");
        Objects.requireNonNull(optionTemplates, "optionTemplates");
        Objects.requireNonNull(defaultOptions, "defaultOptions");

        List<Shop> shops = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("select * from shop order by npc_id asc");
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                Shop shop = new Shop();
                shop.id = result.getInt("id");
                shop.npcId = result.getInt("npc_id");
                shop.tagName = result.getString("tag_name");
                shop.typeShop = result.getByte("type_shop");
                loadShopTabs(connection, shop, itemTemplates, optionTemplates, defaultOptions);
                shops.add(shop);
            }
        }
        return shops;
    }

    private static void loadShopTabs(Connection connection, Shop shop,
            List<ItemTemplate> itemTemplates, List<ItemOptionTemplate> optionTemplates,
            Map<Short, List<Item.ItemOption>> defaultOptions) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "select * from tab_shop where shop_id = ? order by id")) {
            statement.setInt(1, shop.id);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    TabShop tab = new TabShop();
                    tab.shop = shop;
                    tab.id = result.getInt("id");
                    tab.name = result.getString("name").replace("<>", "\n");
                    loadShopItems(connection, tab, itemTemplates, optionTemplates, defaultOptions);
                    shop.tabShops.add(tab);
                }
            }
        }
    }

    private static void loadShopItems(Connection connection, TabShop tab,
            List<ItemTemplate> itemTemplates, List<ItemOptionTemplate> optionTemplates,
            Map<Short, List<Item.ItemOption>> defaultOptions) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "select * from item_shop where is_sell = 1 and tab_id = ? order by create_time desc, id asc")) {
            int persistedTabId = tab.id >= 41 && tab.id <= 43 ? tab.id - 31 : tab.id;
            statement.setInt(1, persistedTabId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    ItemShop item = new ItemShop();
                    item.tabShop = tab;
                    item.id = result.getInt("id");
                    short templateId = result.getShort("temp_id");
                    item.temp = requireItemTemplate(itemTemplates, templateId, item.id);
                    item.isNew = result.getBoolean("is_new");
                    item.cost = result.getInt("cost");
                    item.iconSpec = result.getInt("icon_spec");
                    try {
                        item.optionMode = result.getByte("option_mode");
                    } catch (SQLException ignored) {
                        item.optionMode = 1;
                    }
                    item.typeSell = result.getByte("type_sell");
                    loadShopItemOptions(connection, item, optionTemplates, defaultOptions);
                    tab.itemShops.add(item);
                }
            }
        }
    }

    private static void loadShopItemOptions(Connection connection, ItemShop item,
            List<ItemOptionTemplate> optionTemplates,
            Map<Short, List<Item.ItemOption>> defaultOptions) throws SQLException {
        boolean includeDefaults = item.optionMode == 0 || item.optionMode == 2;
        if (item.optionMode == 0) {
            item.options.addAll(copyOptions(defaultOptions.get(item.temp.id)));
            return;
        }

        List<Item.ItemOption> customOptions = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "select * from item_shop_option where item_shop_id = ?")) {
            statement.setInt(1, item.id);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    int optionId = result.getInt("option_id");
                    ItemOptionTemplate template = requireOptionTemplate(optionTemplates, optionId, item.id);
                    customOptions.add(new Item.ItemOption(template, result.getInt("param")));
                }
            }
        }
        item.options.addAll(mergeOptions(item.temp.id, includeDefaults, customOptions, defaultOptions));
    }

    private static ItemTemplate requireItemTemplate(List<ItemTemplate> templates,
            short templateId, int shopItemId) throws SQLException {
        if (templateId < 0 || templateId >= templates.size()
                || templates.get(templateId).id != templateId) {
            throw new SQLException("item_shop " + shopItemId
                    + " references unknown item_template " + templateId);
        }
        return templates.get(templateId);
    }

    private static ItemOptionTemplate requireOptionTemplate(List<ItemOptionTemplate> templates,
            int optionId, int shopItemId) throws SQLException {
        if (optionId < 0 || optionId >= templates.size() || templates.get(optionId).id != optionId) {
            throw new SQLException("item_shop_option for item_shop " + shopItemId
                    + " references unknown item_option_template " + optionId);
        }
        return templates.get(optionId);
    }

    private static List<Item.ItemOption> mergeOptions(short itemTemplateId, boolean includeDefaults,
            List<Item.ItemOption> customOptions,
            Map<Short, List<Item.ItemOption>> defaultOptions) {
        List<Item.ItemOption> merged = includeDefaults
                ? copyOptions(defaultOptions.get(itemTemplateId)) : new ArrayList<>();
        for (Item.ItemOption custom : customOptions) {
            int existing = -1;
            for (int index = 0; index < merged.size(); index++) {
                if (merged.get(index).optionTemplate.id == custom.optionTemplate.id) {
                    existing = index;
                    break;
                }
            }
            if (existing >= 0) merged.set(existing, new Item.ItemOption(custom));
            else merged.add(new Item.ItemOption(custom));
        }
        return merged;
    }

    private static List<Item.ItemOption> copyOptions(List<Item.ItemOption> source) {
        List<Item.ItemOption> copy = new ArrayList<>();
        if (source != null) {
            for (Item.ItemOption option : source) copy.add(new Item.ItemOption(option));
        }
        return copy;
    }
}
