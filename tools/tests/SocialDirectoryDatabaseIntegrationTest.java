package nro.models.social;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/** Exercises the Phase-3 JDBC projection in an isolated, disposable local schema. */
public final class SocialDirectoryDatabaseIntegrationTest {

    private static final Pattern LOCAL_MYSQL_URL = Pattern.compile(
            "jdbc:mysql://(?:127\\.0\\.0\\.1|localhost):[0-9]{2,5}/");
    private static final Instant NOW = Instant.now();

    private SocialDirectoryDatabaseIntegrationTest() {
    }

    public static void main(String[] args) throws Exception {
        String baseUrl = requiredProperty("social.v2.test.jdbcUrl");
        String user = requiredProperty("social.v2.test.dbUser");
        if (!LOCAL_MYSQL_URL.matcher(baseUrl).matches()) {
            throw new IllegalArgumentException("Test JDBC URL must target local MySQL/MariaDB and end with '/'");
        }
        String password = System.getenv().getOrDefault("SOCIAL_V2_TEST_DB_PASSWORD", "");
        Class.forName("com.mysql.jdbc.Driver");
        String databaseName = "codex_social_v2_phase3_" + UUID.randomUUID().toString().replace("-", "");
        boolean created = false;
        try (Connection admin = open(baseUrl, user, password)) {
            try (Statement statement = admin.createStatement()) {
                statement.execute("CREATE DATABASE `" + databaseName
                        + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci");
                created = true;
            }
            String databaseUrl = baseUrl + databaseName;
            try (Connection connection = open(databaseUrl, user, password)) {
                createMinimalPlayerSchema(connection);
                applyMigration(connection);
                seedDirectory(connection);
                verifyDirectoryProjection(connection, databaseUrl, user, password);
            }
        } finally {
            if (created) {
                dropGeneratedDatabase(baseUrl, user, password, databaseName);
            }
        }
        System.out.println("SocialDirectoryDatabaseIntegrationTest: PASS");
    }

    private static void createMinimalPlayerSchema(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE player (id INT NOT NULL, name VARCHAR(20) NOT NULL, head SMALLINT NOT NULL, "
                    + "friends LONGTEXT NULL, PRIMARY KEY (id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 "
                    + "COLLATE=utf8mb4_general_ci");
        }
    }

    private static void applyMigration(Connection connection) throws Exception {
        String source = Files.readString(Path.of("sql", "migrations", "20260914_add_social_v2_friendship.sql"),
                StandardCharsets.UTF_8);
        String executable = source.lines().filter(line -> !line.stripLeading().startsWith("--"))
                .reduce("", (left, line) -> left + line + "\n");
        try (Statement statement = connection.createStatement()) {
            for (String candidate : executable.split(";")) {
                String sql = candidate.trim();
                if (!sql.isEmpty()) {
                    statement.execute(sql);
                }
            }
        }
    }

    private static void seedDirectory(Connection connection) throws SQLException {
        try (PreparedStatement player = connection.prepareStatement(
                "INSERT INTO player (id,name,head,friends) VALUES (?,?,?,NULL)")) {
            addPlayer(player, 1L, "Viewer", (short) 1);
            addPlayer(player, 2L, "An", (short) 2);
            addPlayer(player, 3L, "Anh", (short) 3);
            addPlayer(player, 4L, "Banan", (short) 4);
            addPlayer(player, 5L, "%_literal", (short) 5);
            addPlayer(player, 50L, "Numeric Target", (short) 50);
            for (long id = 20L; id < 45L; id++) {
                addPlayer(player, id, "Page" + id, (short) id);
            }
            player.executeBatch();
        }
        try (PreparedStatement friendship = connection.prepareStatement(
                "INSERT INTO player_friendship (player_low_id,player_high_id,created_at) VALUES (?,?,?)")) {
            friendship.setLong(1, 1L);
            friendship.setLong(2, 2L);
            friendship.setTimestamp(3, Timestamp.from(NOW));
            friendship.executeUpdate();
        }
        try (PreparedStatement request = connection.prepareStatement(
                "INSERT INTO friend_request (pair_low_id,pair_high_id,sender_id,receiver_id,created_at,expires_at) "
                + "VALUES (?,?,?,?,?,?)")) {
            addRequest(request, 1L, 3L, 3L, 1L, NOW.plusSeconds(300L));
            addRequest(request, 1L, 4L, 4L, 1L, NOW.minusSeconds(1L));
            request.executeBatch();
        }
    }

    private static void verifyDirectoryProjection(Connection connection, String databaseUrl, String user, String password)
            throws Exception {
        JdbcSocialDirectoryRepository repository = new JdbcSocialDirectoryRepository(
                () -> open(databaseUrl, user, password));
        SocialDirectoryService directory = new SocialDirectoryService(repository, playerId -> playerId == 2L);

        SocialDirectoryService.Page<SocialDirectoryRepository.SearchEntry> ranked = directory.search(1L, 10, 0, "an");
        assertEquals("ranked result ids", List.of(2L, 3L, 4L), ids(ranked.entries()));
        assertEquals("friend relationship", SocialDirectoryRepository.Relationship.FRIEND,
                ranked.entries().get(0).relationship());
        assertEquals("pending relationship", SocialDirectoryRepository.Relationship.PENDING,
                ranked.entries().get(1).relationship());
        assertEquals("expired request is not pending", SocialDirectoryRepository.Relationship.CAN_ADD,
                ranked.entries().get(2).relationship());
        assertEquals("numeric exact id", List.of(50L), ids(directory.search(1L, 11, 0, "50").entries()));
        assertEquals("LIKE wildcard stays literal", List.of(5L), ids(directory.search(1L, 12, 0, "%_").entries()));

        SocialDirectoryService.Page<SocialDirectoryRepository.SearchEntry> firstPage = directory.search(1L, 13, 0, "pa");
        SocialDirectoryService.Page<SocialDirectoryRepository.SearchEntry> secondPage = directory.search(1L, 13,
                firstPage.nextCursor(), "pa");
        assertEquals("first page size", SocialV2Protocol.PAGE_SIZE, firstPage.entries().size());
        assertTrue("first page has next", firstPage.hasMore());
        assertEquals("last page size", 5, secondPage.entries().size());
        assertTrue("last page ends cursor", !secondPage.hasMore() && secondPage.nextCursor() == 0);
        Set<Long> allPageIds = new LinkedHashSet<>(ids(firstPage.entries()));
        allPageIds.addAll(ids(secondPage.entries()));
        assertEquals("paging has no duplicate id", 25, allPageIds.size());

        SocialDirectoryService.Page<SocialDirectoryRepository.InboxEntry> inbox = directory.inbox(1L, 20, 0, NOW);
        assertEquals("only non-expired inbox request", 1, inbox.entries().size());
        assertEquals("inbox sender", 3L, inbox.entries().get(0).sender().playerId());
        SocialDirectoryService.FriendList list = directory.friendList(1L, NOW);
        assertEquals("normalized friends", List.of(2L), list.friends().stream()
                .map(SocialDirectoryRepository.PlayerSummary::playerId).toList());
        assertEquals("online friend count", 1, list.onlineFriendCount());
        assertEquals("pending count", 1, list.pendingRequestCount());
    }

    private static List<Long> ids(List<SocialDirectoryRepository.SearchEntry> entries) {
        return entries.stream().map(entry -> entry.player().playerId()).toList();
    }

    private static void addPlayer(PreparedStatement statement, long id, String name, short head) throws SQLException {
        statement.setLong(1, id);
        statement.setString(2, name);
        statement.setShort(3, head);
        statement.addBatch();
    }

    private static void addRequest(PreparedStatement statement, long low, long high, long sender, long receiver,
            Instant expiresAt) throws SQLException {
        statement.setLong(1, low);
        statement.setLong(2, high);
        statement.setLong(3, sender);
        statement.setLong(4, receiver);
        statement.setTimestamp(5, Timestamp.from(NOW));
        statement.setTimestamp(6, Timestamp.from(expiresAt));
        statement.addBatch();
    }

    private static Connection open(String url, String user, String password) throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    private static void dropGeneratedDatabase(String baseUrl, String user, String password, String databaseName)
            throws SQLException {
        if (!databaseName.matches("codex_social_v2_phase3_[0-9a-f]{32}")) {
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
