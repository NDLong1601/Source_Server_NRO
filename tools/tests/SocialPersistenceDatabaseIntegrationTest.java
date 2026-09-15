package nro.models.social;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Pattern;

/**
 * Runs the Social V2 DDL and JDBC repository against an isolated local
 * MySQL/MariaDB schema. The generated schema name is never user-controlled and
 * is dropped in finally, so this test never reads or writes the running game
 * database.
 */
public final class SocialPersistenceDatabaseIntegrationTest {

    private static final Pattern LOCAL_MYSQL_URL = Pattern.compile(
            "jdbc:mysql://(?:127\\.0\\.0\\.1|localhost):[0-9]{2,5}/");
    private static final Instant NOW = Instant.parse("2026-09-14T00:00:00Z");

    private SocialPersistenceDatabaseIntegrationTest() {
    }

    public static void main(String[] args) throws Exception {
        String baseUrl = requiredProperty("social.v2.test.jdbcUrl");
        String user = requiredProperty("social.v2.test.dbUser");
        if (!LOCAL_MYSQL_URL.matcher(baseUrl).matches()) {
            throw new IllegalArgumentException("Test JDBC URL must target local MySQL/MariaDB and end with '/'");
        }
        String password = System.getenv().getOrDefault("SOCIAL_V2_TEST_DB_PASSWORD", "");
        // The legacy Connector/J bundled in 20.jar predates JDBC service loading.
        Class.forName("com.mysql.jdbc.Driver");
        String databaseName = "social_v2_persistence_test_" + UUID.randomUUID().toString().replace("-", "");
        boolean created = false;
        try (Connection admin = open(baseUrl, user, password)) {
            try (Statement statement = admin.createStatement()) {
                statement.execute("CREATE DATABASE `" + databaseName
                        + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci");
                created = true;
            }
            try (Connection connection = open(baseUrl + databaseName, user, password)) {
                createMinimalPlayerSchema(connection);
                applyMigration(connection);
                verifySchema(connection);
                seedPlayers(connection, 410);
                verifyRelationshipTransitions(connection, user, password, baseUrl + databaseName);
                verifyBackfillDryRun(connection);
            }
        } finally {
            if (created) {
                dropGeneratedDatabase(baseUrl, user, password, databaseName);
            }
        }
        System.out.println("SocialPersistenceDatabaseIntegrationTest: PASS");
    }

    private static void createMinimalPlayerSchema(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE player (id INT NOT NULL, friends LONGTEXT NULL, PRIMARY KEY (id)) "
                    + "ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci");
        }
    }

    private static void applyMigration(Connection connection) throws Exception {
        String source = Files.readString(Path.of("sql", "migrations", "20260914_add_social_v2_friendship.sql"),
                StandardCharsets.UTF_8);
        String executableSource = source.lines()
                .filter(line -> !line.stripLeading().startsWith("--"))
                .reduce("", (left, line) -> left + line + "\n");
        try (Statement statement = connection.createStatement()) {
            for (String rawStatement : executableSource.split(";")) {
                String sql = rawStatement.trim();
                if (!sql.isEmpty()) {
                    statement.execute(sql);
                }
            }
        }
    }

    private static void verifySchema(Connection connection) throws SQLException {
        assertEquals("friendship table engine", "InnoDB", tableEngine(connection, "player_friendship"));
        assertEquals("request table engine", "InnoDB", tableEngine(connection, "friend_request"));
        assertTrue("friendship reverse index", indexExists(connection, "player_friendship", "idx_player_friendship_high"));
        assertTrue("pending pair uniqueness", indexExists(connection, "friend_request", "uk_friend_request_pair"));
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO player_friendship (player_low_id,player_high_id,created_at) VALUES (?,?,CURRENT_TIMESTAMP)")) {
            statement.setLong(1, 2L);
            statement.setLong(2, 1L);
            try {
                statement.executeUpdate();
                throw new AssertionError("canonical friendship constraint accepted a reverse pair");
            } catch (SQLException expected) {
                // MySQL/MariaDB enforced the CHECK constraint from the actual migration.
            }
        }
    }

    private static void seedPlayers(Connection connection, int maximumId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO player (id,friends) VALUES (?,?)")) {
            for (int id = 1; id <= maximumId; id++) {
                statement.setInt(1, id);
                statement.setNull(2, java.sql.Types.LONGVARCHAR);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static void verifyRelationshipTransitions(Connection connection, String user, String password,
            String databaseUrl) throws Exception {
        JdbcSocialRelationshipRepository repository = new JdbcSocialRelationshipRepository(
                () -> open(databaseUrl, user, password));
        SocialRelationshipService service = new SocialRelationshipService(repository, new SocialFriendPolicy());

        SocialRelationshipService.Result first = service.sendRequest(1L, 2L, NOW);
        assertStatus("first request", SocialRelationshipService.Status.REQUEST_SENT, first);
        assertTrue("request id generated", first.requestId() > 0L);
        SocialRelationshipService.Result duplicate = service.sendRequest(1L, 2L, NOW);
        assertStatus("duplicate request", SocialRelationshipService.Status.ALREADY_PENDING, duplicate);
        assertEquals("duplicate keeps id", first.requestId(), duplicate.requestId());
        assertStatus("cross request auto accepts", SocialRelationshipService.Status.AUTO_ACCEPTED,
                service.sendRequest(2L, 1L, NOW));
        assertEquals("cross request created exactly one friendship", 1L,
                count(connection, "SELECT COUNT(*) FROM player_friendship WHERE player_low_id=1 AND player_high_id=2"));
        assertEquals("cross request cleared pending row", 0L,
                count(connection, "SELECT COUNT(*) FROM friend_request WHERE pair_low_id=1 AND pair_high_id=2"));

        SocialRelationshipService.Result pending = service.sendRequest(3L, 4L, NOW);
        assertStatus("request for authorization/expiry", SocialRelationshipService.Status.REQUEST_SENT, pending);
        assertStatus("only receiver may reject", SocialRelationshipService.Status.NOT_REQUEST_RECIPIENT,
                service.rejectRequest(3L, pending.requestId()));
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE friend_request SET expires_at=? WHERE id=?")) {
            statement.setTimestamp(1, Timestamp.from(NOW.minusSeconds(1L)));
            statement.setLong(2, pending.requestId());
            statement.executeUpdate();
        }
        assertStatus("expired request", SocialRelationshipService.Status.REQUEST_EXPIRED,
                service.acceptRequest(4L, pending.requestId(), NOW));
        assertEquals("expired request is deleted", 0L,
                count(connection, "SELECT COUNT(*) FROM friend_request WHERE id=" + pending.requestId()));

        SocialRelationshipService.Result concurrentRequest = service.sendRequest(5L, 6L, NOW);
        assertStatus("request before concurrent accept", SocialRelationshipService.Status.REQUEST_SENT, concurrentRequest);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<SocialRelationshipService.Result>> results = executor.invokeAll(List.of(
                    () -> service.acceptRequest(6L, concurrentRequest.requestId(), NOW),
                    () -> service.acceptRequest(6L, concurrentRequest.requestId(), NOW)));
            int accepted = 0;
            int missing = 0;
            for (Future<SocialRelationshipService.Result> future : results) {
                SocialRelationshipService.Status status = future.get().status();
                if (status == SocialRelationshipService.Status.REQUEST_ACCEPTED) {
                    accepted++;
                } else if (status == SocialRelationshipService.Status.REQUEST_NOT_FOUND) {
                    missing++;
                } else {
                    throw new AssertionError("concurrent accept returned " + status);
                }
            }
            assertEquals("one concurrent accept succeeds", 1, accepted);
            assertEquals("one concurrent accept observes deleted request", 1, missing);
        } finally {
            executor.shutdownNow();
        }
        assertEquals("concurrent accept leaves one friendship", 1L,
                count(connection, "SELECT COUNT(*) FROM player_friendship WHERE player_low_id=5 AND player_high_id=6"));

        ExecutorService removeExecutor = Executors.newFixedThreadPool(2);
        try {
            List<Future<SocialRelationshipService.Result>> results = removeExecutor.invokeAll(List.of(
                    () -> service.removeFriendship(5L, 6L),
                    () -> service.removeFriendship(6L, 5L)));
            int removed = 0;
            int absent = 0;
            for (Future<SocialRelationshipService.Result> future : results) {
                SocialRelationshipService.Status status = future.get().status();
                if (status == SocialRelationshipService.Status.FRIENDSHIP_REMOVED) {
                    removed++;
                } else if (status == SocialRelationshipService.Status.NOT_FRIENDS) {
                    absent++;
                } else {
                    throw new AssertionError("concurrent remove returned " + status);
                }
            }
            assertEquals("one concurrent remove succeeds", 1, removed);
            assertEquals("one concurrent remove observes absence", 1, absent);
        } finally {
            removeExecutor.shutdownNow();
        }

        addFriendships(connection, 70L, 100L, 199L);
        assertStatus("sender limit is enforced", SocialRelationshipService.Status.FRIEND_LIMIT_REACHED,
                service.sendRequest(70L, 71L, NOW));
        addFriendships(connection, 80L, 200L, 299L);
        assertStatus("receiver limit is enforced", SocialRelationshipService.Status.TARGET_FRIEND_LIMIT_REACHED,
                service.sendRequest(81L, 80L, NOW));
        assertStatus("self target is invalid", SocialRelationshipService.Status.INVALID_TARGET,
                service.sendRequest(9L, 9L, NOW));
        assertStatus("missing target is rejected", SocialRelationshipService.Status.TARGET_NOT_FOUND,
                service.sendRequest(9L, 999L, NOW));
    }

    private static void addFriendships(Connection connection, long playerId, long firstFriendId, long lastFriendId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO player_friendship (player_low_id,player_high_id,created_at) VALUES (?,?,CURRENT_TIMESTAMP)")) {
            for (long friendId = firstFriendId; friendId <= lastFriendId; friendId++) {
                statement.setLong(1, Math.min(playerId, friendId));
                statement.setLong(2, Math.max(playerId, friendId));
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static void verifyBackfillDryRun(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("UPDATE player SET friends=? WHERE id=?")) {
            statement.setString(1, "[\"[301,\\\"legacy\\\",0,0,0,0,0]\",\"malformed\"]");
            statement.setLong(2, 300L);
            statement.addBatch();
            statement.setString(1, "[\"[300,\\\"reverse\\\",0,0,0,0,0]\"]");
            statement.setLong(2, 301L);
            statement.addBatch();
            statement.executeBatch();
        }
        LegacyFriendBackfill.ExecutionReport report = LegacyFriendBackfill.run(connection, false);
        assertEquals("backfill union is canonical", 1L, report.analysis().uniquePairs());
        assertEquals("backfill counts malformed entry", 1L, report.analysis().skippedMalformedLinks());
        assertEquals("dry run writes nothing", 0L, report.inserted());
        assertTrue("dry run is not apply", !report.applied());
        assertEquals("dry run did not create friendship", 0L,
                count(connection, "SELECT COUNT(*) FROM player_friendship WHERE player_low_id=300 AND player_high_id=301"));
        LegacyFriendBackfill.ExecutionReport applied = LegacyFriendBackfill.run(connection, true, () -> true);
        assertTrue("test-gated apply reports apply", applied.applied());
        assertEquals("backfill apply inserts one canonical pair", 1L, applied.inserted());
        LegacyFriendBackfill.ExecutionReport repeated = LegacyFriendBackfill.run(connection, true, () -> true);
        assertEquals("repeated backfill sees existing pair", 1L, repeated.alreadyPresent());
        assertEquals("repeated backfill inserts no duplicate", 0L, repeated.inserted());
        assertEquals("applied backfill creates one canonical friendship", 1L,
                count(connection, "SELECT COUNT(*) FROM player_friendship WHERE player_low_id=300 AND player_high_id=301"));
        try {
            LegacyFriendBackfill.run(connection, true);
            throw new AssertionError("backfill apply bypassed disabled feature flags");
        } catch (IllegalStateException expected) {
            // Apply stays fail-closed until the explicit rollout flags are both enabled.
        }
    }

    private static String tableEngine(Connection connection, String tableName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=?")) {
            statement.setString(1, tableName);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    throw new AssertionError("missing table " + tableName);
                }
                return rows.getString(1);
            }
        }
    }

    private static boolean indexExists(Connection connection, String tableName, String indexName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? AND INDEX_NAME=?")) {
            statement.setString(1, tableName);
            statement.setString(2, indexName);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next();
            }
        }
    }

    private static long count(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
            if (!rows.next()) {
                throw new AssertionError("missing scalar result");
            }
            return rows.getLong(1);
        }
    }

    private static Connection open(String url, String user, String password) throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    private static void dropGeneratedDatabase(String baseUrl, String user, String password, String databaseName)
            throws SQLException {
        if (!databaseName.matches("social_v2_persistence_test_[0-9a-f]{32}")) {
            throw new IllegalStateException("Refusing to drop an unrecognized test database");
        }
        try (Connection admin = open(baseUrl, user, password); Statement statement = admin.createStatement()) {
            statement.execute("DROP DATABASE `" + databaseName + "`");
        }
    }

    private static String requiredProperty(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required test property " + name);
        }
        return value;
    }

    private static void assertStatus(String label, SocialRelationshipService.Status expected,
            SocialRelationshipService.Result actual) {
        assertEquals(label, expected, actual.status());
    }

    private static void assertTrue(String label, boolean value) {
        if (!value) {
            throw new AssertionError(label);
        }
    }

    private static void assertEquals(String label, Object expected, Object actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError(label + " expected=" + expected + " actual=" + actual);
        }
    }
}
