package nro.models.database;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import nro.models.data.LocalManager;
import nro.models.item.Item;
import nro.models.player.Player;
import nro.models.services_func.TradeCommitPlan.ImmutableTradeData;
import nro.models.services_func.TradeLedgerSink;
import nro.models.utils.Logger;
import nro.models.utils.TimeUtil;

/** SEC-03 parameterized trade ledger and legacy transaction history access. */
public class HistoryTransactionDAO {

    private static final String LEDGER_COLUMNS = "trade_id, player_id_1, player_id_2, player_name_1, player_name_2, "
            + "status, result_code, gold_trade_1, gold_trade_2, "
            + "items_trade_1_json, items_trade_2_json, "
            + "bag_1_before_json, bag_2_before_json, bag_1_after_json, bag_2_after_json, "
            + "gold_1_before, gold_2_before, gold_1_after, gold_2_after";

    private static final String INSERT_LEDGER_SQL = "INSERT INTO trade_transaction_ledger ("
            + LEDGER_COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final TradeLedgerSink LEDGER_SINK = new TradeLedgerSink() {
        @Override
        public boolean begin(ImmutableTradeData data) {
            return insertLedgerRow(data, "COMMITTING", "PENDING", false);
        }

        @Override
        public boolean committed(ImmutableTradeData data) {
            return markLedgerCommitted(data);
        }

        @Override
        public boolean terminal(ImmutableTradeData data, String status, String resultCode) {
            return insertLedgerRow(data, status, resultCode, true);
        }
    };

    public static TradeLedgerSink ledgerSink() {
        return LEDGER_SINK;
    }

    private static boolean insertLedgerRow(ImmutableTradeData data, String status,
                                           String resultCode, boolean upsert) {
        if (data == null) {
            return false;
        }
        String sql = INSERT_LEDGER_SQL;
        if (upsert) {
            sql += " ON DUPLICATE KEY UPDATE status=VALUES(status), result_code=VALUES(result_code), "
                    + "bag_1_after_json=VALUES(bag_1_after_json), bag_2_after_json=VALUES(bag_2_after_json), "
                    + "gold_1_after=VALUES(gold_1_after), gold_2_after=VALUES(gold_2_after)";
        }
        try (Connection con = LocalManager.getConnection()) {
            if (con == null) {
                Logger.error(String.format("[TradeLedger] DB unavailable tradeId=%s p1=%d p2=%d status=%s",
                        data.tradeId, data.player1Id, data.player2Id, status));
                return false;
            }
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                bindLedger(ps, data, status, resultCode);
                return ps.executeUpdate() > 0;
            }
        } catch (Exception ex) {
            Logger.error(String.format("[TradeLedger] Persist failed tradeId=%s p1=%d p2=%d status=%s error=%s",
                    data.tradeId, data.player1Id, data.player2Id, status, ex.getMessage()));
            return false;
        }
    }

    private static boolean markLedgerCommitted(ImmutableTradeData data) {
        if (data == null) {
            return false;
        }
        String sql = "UPDATE trade_transaction_ledger SET status=?, result_code=?, "
                + "bag_1_after_json=?, bag_2_after_json=?, gold_1_after=?, gold_2_after=? WHERE trade_id=?";
        boolean updated = false;
        try (Connection con = LocalManager.getConnection()) {
            if (con == null) {
                Logger.error("[TradeLedger] DB unavailable while committing tradeId=" + data.tradeId);
                return false;
            }
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, "COMMITTED");
                ps.setString(2, "SUCCESS");
                ps.setString(3, formatItemList(data.bag1After));
                ps.setString(4, formatItemList(data.bag2After));
                ps.setLong(5, data.gold1After);
                ps.setLong(6, data.gold2After);
                ps.setString(7, data.tradeId);
                updated = ps.executeUpdate() == 1;
            }
        } catch (Exception ex) {
            Logger.error(String.format("[TradeLedger] Commit update failed tradeId=%s p1=%d p2=%d error=%s",
                    data.tradeId, data.player1Id, data.player2Id, ex.getMessage()));
        }
        if (updated) {
            insertLegacy(data);
        }
        return updated;
    }

    private static void bindLedger(PreparedStatement ps, ImmutableTradeData data,
                                   String status, String resultCode) throws SQLException {
        ps.setString(1, data.tradeId);
        ps.setLong(2, data.player1Id);
        ps.setLong(3, data.player2Id);
        ps.setString(4, data.player1Name != null ? data.player1Name : "Unknown");
        ps.setString(5, data.player2Name != null ? data.player2Name : "Unknown");
        ps.setString(6, status);
        ps.setString(7, resultCode);
        ps.setLong(8, data.goldTrade1);
        ps.setLong(9, data.goldTrade2);
        ps.setString(10, formatItemList(data.itemsTrade1));
        ps.setString(11, formatItemList(data.itemsTrade2));
        ps.setString(12, formatItemList(data.bag1Before));
        ps.setString(13, formatItemList(data.bag2Before));
        ps.setString(14, formatItemList(data.bag1After));
        ps.setString(15, formatItemList(data.bag2After));
        ps.setLong(16, data.gold1Before);
        ps.setLong(17, data.gold2Before);
        ps.setLong(18, data.gold1After);
        ps.setLong(19, data.gold2After);
    }

    private static void insertLegacy(ImmutableTradeData data) {
        String p1Label = data.player1Name + " (" + data.player1Id + ")";
        String p2Label = data.player2Name + " (" + data.player2Id + ")";
        try {
            LocalManager.executeUpdate("INSERT INTO history_transaction (player_1, player_2, item_player_1, item_player_2, "
                    + "bag_1_before_tran, bag_2_before_tran, bag_1_after_tran, bag_2_after_tran, time_tran) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    p1Label, p2Label,
                    "Gold: " + data.goldTrade1 + ", " + formatItemList(data.itemsTrade1),
                    "Gold: " + data.goldTrade2 + ", " + formatItemList(data.itemsTrade2),
                    formatItemList(data.bag1Before), formatItemList(data.bag2Before),
                    formatItemList(data.bag1After), formatItemList(data.bag2After),
                    new Timestamp(System.currentTimeMillis()));
        } catch (Exception ex) {
            Logger.error(String.format("[TradeLedger] Legacy history failed tradeId=%s error=%s",
                    data.tradeId, ex.getMessage()));
        }
    }

    /** Legacy source-compatible adapter. New trade code uses {@link #ledgerSink()}. */
    public static void insertLedger(ImmutableTradeData data, String status, String resultCode) {
        insertLedgerRow(data, status, resultCode, true);
    }

    /** Serializes item snapshots as valid JSON for the *_json ledger columns. */
    public static String formatItemList(List<Item> items) {
        JsonArray array = new JsonArray();
        if (items == null) {
            return array.toString();
        }
        for (Item item : items) {
            if (item == null || !item.isNotNullItem()) {
                continue;
            }
            JsonObject value = new JsonObject();
            value.addProperty("templateId", item.template.id);
            value.addProperty("name", item.template.name != null ? item.template.name : "");
            value.addProperty("quantity", item.quantity);
            value.addProperty("createTime", item.createTime);
            JsonArray options = new JsonArray();
            if (item.itemOptions != null) {
                for (Item.ItemOption option : item.itemOptions) {
                    if (option == null || option.optionTemplate == null) {
                        continue;
                    }
                    JsonObject optionValue = new JsonObject();
                    optionValue.addProperty("id", option.optionTemplate.id);
                    optionValue.addProperty("param", option.param);
                    options.add(optionValue);
                }
            }
            value.add("options", options);
            array.add(value);
        }
        return array.toString();
    }

    /** Legacy insert method preserved for backwards compatibility. */
    public static void insert(Player pl1, Player pl2,
            int goldP1, int goldP2, List<Item> itemP1, List<Item> itemP2,
            List<Item> bag1Before, List<Item> bag2Before,
            List<Item> bag1After, List<Item> bag2After,
            long gold1Before, long gold2Before, long gold1After, long gold2After) {
        String player1 = (pl1 != null ? pl1.name : "null") + " (" + (pl1 != null ? pl1.id : -1) + ")";
        String player2 = (pl2 != null ? pl2.name : "null") + " (" + (pl2 != null ? pl2.id : -1) + ")";
        try {
            LocalManager.executeUpdate("INSERT INTO history_transaction (player_1, player_2, item_player_1, item_player_2, "
                    + "bag_1_before_tran, bag_2_before_tran, bag_1_after_tran, bag_2_after_tran, time_tran) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    player1, player2,
                    "Gold: " + goldP1 + ", " + formatItemList(itemP1),
                    "Gold: " + goldP2 + ", " + formatItemList(itemP2),
                    formatItemList(bag1Before), formatItemList(bag2Before),
                    formatItemList(bag1After), formatItemList(bag2After),
                    new Timestamp(System.currentTimeMillis()));
        } catch (Exception ex) {
            Logger.error("[HistoryTransactionDAO] insert failed: " + ex.getMessage());
        }
    }

    public static void deleteHistory() {
        String cutoff = TimeUtil.getTimeBeforeCurrent(3 * 24 * 60 * 60 * 1000, "yyyy-MM-dd");
        try (Connection con = LocalManager.getConnection()) {
            if (con == null) {
                return;
            }
            try (PreparedStatement ps = con.prepareStatement(
                    "DELETE FROM history_transaction WHERE time_tran < ?")) {
                ps.setString(1, cutoff);
                ps.executeUpdate();
            }
        } catch (Exception ex) {
            Logger.error("[HistoryTransactionDAO] deleteHistory failed: " + ex.getMessage());
        }
    }
}
