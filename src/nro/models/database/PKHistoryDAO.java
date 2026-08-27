package nro.models.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import nro.models.data.LocalManager;
import nro.models.matches.TYPE_LOSE_PVP;
import nro.models.player.Player;
import nro.models.utils.Logger;

/** Persists and reads the recent, completed gold challenge matches of a player. */
public final class PKHistoryDAO {

    public static final int MAX_HISTORY = 20;

    private PKHistoryDAO() {
    }

    public static void insert(Player winner, Player loser, TYPE_LOSE_PVP loseType, int wagerGold) {
        if (winner == null || loser == null) {
            return;
        }

        int mapId = -1;
        int zoneId = -1;
        if (winner.zone != null) {
            zoneId = winner.zone.zoneId;
            if (winner.zone.map != null) {
                mapId = winner.zone.map.mapId;
            }
        }

        try {
            LocalManager.executeUpdate(
                    "INSERT INTO pk_history (winner_id, winner_name, loser_id, loser_name, result_type, wager_gold, map_id, zone_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    winner.id, winner.name, loser.id, loser.name, loseType.ordinal(), wagerGold, mapId, zoneId);
        } catch (Exception exception) {
            // A reporting failure must never change the outcome of a challenge.
            Logger.logException(PKHistoryDAO.class, exception, "Không thể lưu lịch sử PK");
        }
    }

    public static List<Entry> findRecent(long playerId) {
        List<Entry> entries = new ArrayList<>();
        String query = "SELECT winner_id, winner_name, loser_name, UNIX_TIMESTAMP(created_at) AS completed_at "
                + "FROM pk_history WHERE winner_id = ? OR loser_id = ? ORDER BY id DESC LIMIT ?";
        try (Connection connection = LocalManager.getConnection();
                PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setLong(1, playerId);
            statement.setLong(2, playerId);
            statement.setInt(3, MAX_HISTORY);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    boolean won = resultSet.getLong("winner_id") == playerId;
                    String opponentName = won ? resultSet.getString("loser_name") : resultSet.getString("winner_name");
                    entries.add(new Entry(opponentName, won, resultSet.getLong("completed_at")));
                }
            }
        } catch (Exception exception) {
            Logger.logException(PKHistoryDAO.class, exception, "Không thể tải lịch sử PK");
        }
        return entries;
    }

    public static final class Entry {

        public final String opponentName;
        public final boolean won;
        public final long completedAt;

        private Entry(String opponentName, boolean won, long completedAt) {
            this.opponentName = opponentName == null ? "" : opponentName;
            this.won = won;
            this.completedAt = completedAt;
        }
    }
}
