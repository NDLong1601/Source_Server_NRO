package nro.models.social;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import org.json.simple.JSONArray;
import org.json.simple.JSONValue;
import nro.models.data.LocalManager;

/** JDBC implementation of the deliberately minimal offline profile projection. */
public final class JdbcSocialProfileRepository implements SocialProfileRepository {

    private final JdbcSocialRelationshipRepository.ConnectionFactory connections;

    public JdbcSocialProfileRepository() {
        this(LocalManager::getConnection);
    }

    public JdbcSocialProfileRepository(JdbcSocialRelationshipRepository.ConnectionFactory connections) {
        if (connections == null) {
            throw new IllegalArgumentException("Connection factory is required");
        }
        this.connections = connections;
    }

    @Override
    public StoredProfile findByPlayerId(long playerId) throws SQLException {
        if (playerId <= 0L) {
            return null;
        }
        String sql = "SELECT p.id,p.name,p.head,p.data_point,c.NAME AS clan_name,a.last_time_logout "
                + "FROM player p LEFT JOIN clan c ON c.id=p.clan_id "
                + "LEFT JOIN account a ON a.id=p.account_id WHERE p.id=?";
        try (Connection connection = connections.open(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, playerId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return null;
                }
                Timestamp lastLogout = rows.getTimestamp("last_time_logout");
                return new StoredProfile(rows.getLong("id"), rows.getString("name"), rows.getShort("head"),
                        safeClanName(rows.getString("clan_name")), parseRawPower(rows.getString("data_point")),
                        lastLogout == null ? null : lastLogout.toInstant());
            }
        }
    }

    private static String safeClanName(String clanName) {
        return clanName == null ? "" : clanName;
    }

    /** Reads only data_point[1] (power); malformed legacy JSON degrades safely to zero. */
    private static long parseRawPower(String rawDataPoint) {
        try {
            Object parsed = JSONValue.parse(rawDataPoint);
            if (!(parsed instanceof JSONArray values) || values.size() < 2 || values.get(1) == null) {
                return 0L;
            }
            return Math.max(0L, Long.parseLong(String.valueOf(values.get(1))));
        } catch (RuntimeException malformed) {
            return 0L;
        }
    }
}
