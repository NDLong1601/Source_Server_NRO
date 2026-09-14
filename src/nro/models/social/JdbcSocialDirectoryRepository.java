package nro.models.social;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import nro.models.data.LocalManager;

/** JDBC projection for Phase-3 social reads; no account or inventory column is selected. */
public final class JdbcSocialDirectoryRepository implements SocialDirectoryRepository {

    private final JdbcSocialRelationshipRepository.ConnectionFactory connections;

    public JdbcSocialDirectoryRepository() {
        this(LocalManager::getConnection);
    }

    public JdbcSocialDirectoryRepository(JdbcSocialRelationshipRepository.ConnectionFactory connections) {
        if (connections == null) {
            throw new IllegalArgumentException("Connection factory is required");
        }
        this.connections = connections;
    }

    @Override
    public List<SearchEntry> search(long viewerId, String normalizedQuery, int offset, int limit) throws SQLException {
        long numericId = positiveNumericId(normalizedQuery);
        String query = normalizedQuery.toLowerCase(Locale.ROOT);
        String escaped = escapeLike(query);
        String sql = "SELECT p.id,p.name,p.head,"
                + "CASE WHEN f.player_low_id IS NOT NULL THEN 1 WHEN r.id IS NOT NULL THEN 2 ELSE 3 END AS social_state "
                + "FROM player p "
                + "LEFT JOIN player_friendship f ON ((f.player_low_id=? AND f.player_high_id=p.id) "
                + "OR (f.player_high_id=? AND f.player_low_id=p.id)) "
                + "LEFT JOIN friend_request r ON ((r.pair_low_id=? AND r.pair_high_id=p.id) "
                + "OR (r.pair_high_id=? AND r.pair_low_id=p.id)) AND r.expires_at>CURRENT_TIMESTAMP "
                + "WHERE p.id<>? AND (p.id=? OR LOWER(p.name)=? OR LOWER(p.name) LIKE ? ESCAPE '\\\\' "
                + "OR LOWER(p.name) LIKE ? ESCAPE '\\\\') "
                + "ORDER BY CASE WHEN p.id=? THEN 0 WHEN LOWER(p.name)=? THEN 1 "
                + "WHEN LOWER(p.name) LIKE ? THEN 2 ELSE 3 END,p.id ASC LIMIT ?,?";
        try (Connection connection = connections.open(); PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            statement.setLong(index++, viewerId);
            statement.setLong(index++, viewerId);
            statement.setLong(index++, viewerId);
            statement.setLong(index++, viewerId);
            statement.setLong(index++, viewerId);
            statement.setLong(index++, numericId);
            statement.setString(index++, query);
            statement.setString(index++, escaped + "%");
            statement.setString(index++, "%" + escaped + "%");
            statement.setLong(index++, numericId);
            statement.setString(index++, query);
            statement.setString(index++, escaped + "%");
            statement.setInt(index++, offset);
            statement.setInt(index, limit);
            try (ResultSet rows = statement.executeQuery()) {
                List<SearchEntry> entries = new ArrayList<>();
                while (rows.next()) {
                    entries.add(new SearchEntry(summary(rows), relationship(rows.getInt("social_state"))));
                }
                return List.copyOf(entries);
            }
        }
    }

    @Override
    public List<InboxEntry> inbox(long receiverId, int offset, int limit, Instant now) throws SQLException {
        String sql = "SELECT r.id AS request_id,r.expires_at,p.id AS player_id,p.name,p.head FROM friend_request r "
                + "JOIN player p ON p.id=r.sender_id WHERE r.receiver_id=? AND r.expires_at>? "
                + "ORDER BY r.id ASC LIMIT ?,?";
        try (Connection connection = connections.open(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, receiverId);
            statement.setTimestamp(2, Timestamp.from(now));
            statement.setInt(3, offset);
            statement.setInt(4, limit);
            try (ResultSet rows = statement.executeQuery()) {
                List<InboxEntry> entries = new ArrayList<>();
                while (rows.next()) {
                    Timestamp expiresAt = rows.getTimestamp("expires_at");
                    if (expiresAt != null) {
                        entries.add(new InboxEntry(rows.getLong("request_id"),
                                new PlayerSummary(rows.getLong("player_id"), rows.getString("name"),
                                        rows.getShort("head")), expiresAt.toInstant()));
                    }
                }
                return List.copyOf(entries);
            }
        }
    }

    @Override
    public List<PlayerSummary> friends(long playerId) throws SQLException {
        String sql = "SELECT p.id,p.name,p.head FROM player_friendship f JOIN player p ON p.id="
                + "CASE WHEN f.player_low_id=? THEN f.player_high_id ELSE f.player_low_id END "
                + "WHERE f.player_low_id=? OR f.player_high_id=? ORDER BY LOWER(p.name),p.id LIMIT ?";
        try (Connection connection = connections.open(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, playerId);
            statement.setLong(2, playerId);
            statement.setLong(3, playerId);
            statement.setInt(4, SocialV2Protocol.MAX_FRIENDS + 1);
            try (ResultSet rows = statement.executeQuery()) {
                List<PlayerSummary> entries = new ArrayList<>();
                while (rows.next()) {
                    entries.add(summary(rows));
                }
                return List.copyOf(entries);
            }
        }
    }

    @Override
    public int pendingCount(long receiverId, Instant now) throws SQLException {
        try (Connection connection = connections.open(); PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM friend_request WHERE receiver_id=? AND expires_at>?")) {
            statement.setLong(1, receiverId);
            statement.setTimestamp(2, Timestamp.from(now));
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? rows.getInt(1) : 0;
            }
        }
    }

    private static PlayerSummary summary(ResultSet rows) throws SQLException {
        return new PlayerSummary(rows.getLong("id"), rows.getString("name"), rows.getShort("head"));
    }

    private static Relationship relationship(int value) {
        return switch (value) {
            case 1 -> Relationship.FRIEND;
            case 2 -> Relationship.PENDING;
            default -> Relationship.CAN_ADD;
        };
    }

    private static long positiveNumericId(String value) {
        if (value == null || !value.chars().allMatch(Character::isDigit)) {
            return -1L;
        }
        try {
            long parsed = Long.parseLong(value);
            return parsed > 0L ? parsed : -1L;
        } catch (NumberFormatException overflow) {
            return -1L;
        }
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
