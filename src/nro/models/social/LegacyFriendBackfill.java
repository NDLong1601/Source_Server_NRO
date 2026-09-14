package nro.models.social;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import org.json.simple.JSONArray;
import org.json.simple.JSONValue;
import nro.models.data.LocalManager;

/**
 * One-time, idempotent migration helper for legacy player.friends JSON.
 * Dry-run is the default; apply additionally requires both social-v2 flags.
 */
public final class LegacyFriendBackfill {

    public record LegacyRow(long playerId, String friendsJson) {
    }

    public record Analysis(long sourceRows, long sourceLinks, long uniquePairs,
            long skippedMalformedRows, long skippedMalformedLinks, long skippedSelfLinks,
            long skippedMissingPlayers, Set<SocialFriendPolicy.FriendshipPair> pairs) {
        public Analysis {
            pairs = Collections.unmodifiableSet(new LinkedHashSet<>(pairs));
        }
    }

    public record ExecutionReport(Analysis analysis, long alreadyPresent, long inserted, boolean applied) {
        public long wouldInsert() {
            return Math.max(0L, analysis.uniquePairs() - alreadyPresent);
        }

        public String summary() {
            return "Social legacy backfill: rows=" + analysis.sourceRows()
                    + ", links=" + analysis.sourceLinks()
                    + ", uniquePairs=" + analysis.uniquePairs()
                    + ", alreadyPresent=" + alreadyPresent
                    + ", wouldInsert=" + wouldInsert()
                    + ", inserted=" + inserted
                    + ", malformedRows=" + analysis.skippedMalformedRows()
                    + ", malformedLinks=" + analysis.skippedMalformedLinks()
                    + ", selfLinks=" + analysis.skippedSelfLinks()
                    + ", missingPlayers=" + analysis.skippedMissingPlayers()
                    + ", applied=" + applied;
        }
    }

    private LegacyFriendBackfill() {
    }

    public static void main(String[] args) throws Exception {
        boolean apply = args.length == 1 && "--apply".equals(args[0]);
        if (args.length > 1 || (args.length == 1 && !apply && !"--dry-run".equals(args[0]))) {
            throw new IllegalArgumentException("Usage: LegacyFriendBackfill [--dry-run|--apply]");
        }
        if (apply && (!SocialV2FeatureFlags.gI().isEnabled()
                || !SocialV2FeatureFlags.gI().isMigrationEnabled())) {
            throw new IllegalStateException("--apply requires social_v2.enabled and social_v2.migration_enabled");
        }
        try (Connection connection = LocalManager.getConnection()) {
            System.out.println(run(connection, apply).summary());
        }
    }

    public static ExecutionReport run(Connection connection, boolean apply) throws SQLException {
        return run(connection, apply, LegacyFriendBackfill::isApplyEnabled);
    }

    /**
     * Package-private seam for the disposable JDBC integration test. Production
     * callers must use {@link #run(Connection, boolean)}, which always reads the
     * configured rollout flags.
     */
    static ExecutionReport run(Connection connection, boolean apply, BooleanSupplier applyEnabled) throws SQLException {
        if (connection == null) {
            throw new IllegalArgumentException("Connection is required");
        }
        if (applyEnabled == null) {
            throw new IllegalArgumentException("Apply gate is required");
        }
        if (apply && !applyEnabled.getAsBoolean()) {
            throw new IllegalStateException("Apply is disabled by social-v2 rollout flags");
        }
        List<LegacyRow> rows = new ArrayList<>();
        Set<Long> knownPlayerIds = new HashSet<>();
        try (PreparedStatement statement = connection.prepareStatement("SELECT id,friends FROM player");
                ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                long playerId = result.getLong("id");
                knownPlayerIds.add(playerId);
                rows.add(new LegacyRow(playerId, result.getString("friends")));
            }
        }
        Analysis analysis = analyze(rows, knownPlayerIds);
        long alreadyPresent = 0L;
        List<SocialFriendPolicy.FriendshipPair> newPairs = new ArrayList<>();
        for (SocialFriendPolicy.FriendshipPair pair : analysis.pairs()) {
            if (friendshipExists(connection, pair)) {
                alreadyPresent++;
            } else {
                newPairs.add(pair);
            }
        }
        if (!apply || newPairs.isEmpty()) {
            return new ExecutionReport(analysis, alreadyPresent, 0L, false);
        }

        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        long inserted = 0L;
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO player_friendship (player_low_id,player_high_id,created_at) VALUES (?,?,?) "
                        + "ON DUPLICATE KEY UPDATE player_low_id=VALUES(player_low_id)")) {
            TimestampBinder timestamp = new TimestampBinder(statement, Instant.now());
            for (SocialFriendPolicy.FriendshipPair pair : newPairs) {
                statement.setLong(1, pair.lowPlayerId());
                statement.setLong(2, pair.highPlayerId());
                timestamp.bind();
                int affected = statement.executeUpdate();
                if (affected == 1) {
                    inserted++;
                }
            }
            connection.commit();
            return new ExecutionReport(analysis, alreadyPresent, inserted, true);
        } catch (SQLException error) {
            connection.rollback();
            throw error;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    private static boolean isApplyEnabled() {
        return SocialV2FeatureFlags.gI().isEnabled()
                && SocialV2FeatureFlags.gI().isMigrationEnabled();
    }

    public static Analysis analyze(List<LegacyRow> rows, Set<Long> knownPlayerIds) {
        List<LegacyRow> safeRows = rows == null ? List.of() : rows;
        Set<Long> known = knownPlayerIds == null ? Set.of() : new HashSet<>(knownPlayerIds);
        SocialFriendPolicy policy = new SocialFriendPolicy();
        Set<SocialFriendPolicy.FriendshipPair> pairs = new LinkedHashSet<>();
        long sourceRows = 0L;
        long sourceLinks = 0L;
        long malformedRows = 0L;
        long malformedLinks = 0L;
        long selfLinks = 0L;
        long missingPlayers = 0L;
        for (LegacyRow row : safeRows) {
            sourceRows++;
            if (row == null || row.playerId() <= 0L || row.friendsJson() == null || row.friendsJson().isBlank()) {
                continue;
            }
            Object parsed = JSONValue.parse(row.friendsJson());
            if (!(parsed instanceof JSONArray entries)) {
                malformedRows++;
                continue;
            }
            for (Object entry : entries) {
                sourceLinks++;
                Long targetId = legacyTargetId(entry);
                if (targetId == null || targetId <= 0L) {
                    malformedLinks++;
                    continue;
                }
                if (targetId == row.playerId()) {
                    selfLinks++;
                    continue;
                }
                if (!known.contains(row.playerId()) || !known.contains(targetId)) {
                    missingPlayers++;
                    continue;
                }
                try {
                    pairs.add(policy.normalizePair(row.playerId(), targetId));
                } catch (IllegalArgumentException error) {
                    malformedLinks++;
                }
            }
        }
        return new Analysis(sourceRows, sourceLinks, pairs.size(), malformedRows, malformedLinks,
                selfLinks, missingPlayers, pairs);
    }

    private static Long legacyTargetId(Object entry) {
        Object parsed = entry instanceof JSONArray ? entry : JSONValue.parse(String.valueOf(entry));
        if (!(parsed instanceof JSONArray values) || values.isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(values.get(0)));
        } catch (RuntimeException error) {
            return null;
        }
    }

    private static boolean friendshipExists(Connection connection, SocialFriendPolicy.FriendshipPair pair)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM player_friendship WHERE player_low_id=? AND player_high_id=?")) {
            statement.setLong(1, pair.lowPlayerId());
            statement.setLong(2, pair.highPlayerId());
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next();
            }
        }
    }

    private static final class TimestampBinder {
        private final PreparedStatement statement;
        private final java.sql.Timestamp timestamp;

        private TimestampBinder(PreparedStatement statement, Instant now) {
            this.statement = statement;
            this.timestamp = java.sql.Timestamp.from(now);
        }

        private void bind() throws SQLException {
            statement.setTimestamp(3, timestamp);
        }
    }
}
