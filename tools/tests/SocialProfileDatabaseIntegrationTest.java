package nro.models.social;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

/** Exercises the offline profile projection in a disposable local schema. */
public final class SocialProfileDatabaseIntegrationTest {

    private static final Pattern LOCAL_MYSQL_URL = Pattern.compile(
            "jdbc:mysql://(?:127\\.0\\.0\\.1|localhost):[0-9]{2,5}/");

    private SocialProfileDatabaseIntegrationTest() {
    }

    public static void main(String[] args) throws Exception {
        String baseUrl = requiredProperty("social.v2.test.jdbcUrl");
        String user = requiredProperty("social.v2.test.dbUser");
        if (!LOCAL_MYSQL_URL.matcher(baseUrl).matches()) {
            throw new IllegalArgumentException("Test JDBC URL must target local MySQL/MariaDB and end with '/'");
        }
        String password = System.getenv().getOrDefault("SOCIAL_V2_TEST_DB_PASSWORD", "");
        Class.forName("com.mysql.jdbc.Driver");
        String databaseName = "social_v2_profile_test_" + UUID.randomUUID().toString().replace("-", "");
        boolean created = false;
        try (Connection admin = open(baseUrl, user, password)) {
            try (Statement statement = admin.createStatement()) {
                statement.execute("CREATE DATABASE `" + databaseName
                        + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci");
                created = true;
            }
            String databaseUrl = baseUrl + databaseName;
            try (Connection connection = open(databaseUrl, user, password)) {
                createSchema(connection);
                seed(connection);
                verifyProjection(databaseUrl, user, password);
            }
        } finally {
            if (created) {
                dropGeneratedDatabase(baseUrl, user, password, databaseName);
            }
        }
        System.out.println("SocialProfileDatabaseIntegrationTest: PASS");
    }

    private static void createSchema(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE account (id INT NOT NULL PRIMARY KEY,last_time_logout TIMESTAMP NULL) "
                    + "ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            statement.execute("CREATE TABLE clan (id INT NOT NULL PRIMARY KEY,NAME VARCHAR(255) NOT NULL) "
                    + "ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            statement.execute("CREATE TABLE player (id INT NOT NULL PRIMARY KEY,account_id INT NULL,name VARCHAR(20) NOT NULL,"
                    + "head SMALLINT NOT NULL,clan_id INT NOT NULL,data_point TEXT NOT NULL) "
                    + "ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        }
    }

    private static void seed(Connection connection) throws SQLException {
        Instant eightDaysAgo = Instant.parse("2026-09-06T00:00:00Z");
        try (PreparedStatement account = connection.prepareStatement(
                "INSERT INTO account (id,last_time_logout) VALUES (?,?)");
                PreparedStatement clan = connection.prepareStatement("INSERT INTO clan (id,NAME) VALUES (?,?)");
                PreparedStatement player = connection.prepareStatement(
                        "INSERT INTO player (id,account_id,name,head,clan_id,data_point) VALUES (?,?,?,?,?,?)")) {
            account.setInt(1, 77);
            account.setTimestamp(2, Timestamp.from(eightDaysAgo));
            account.executeUpdate();
            clan.setInt(1, 8);
            clan.setString(2, "Clan");
            clan.executeUpdate();
            player.setInt(1, 9);
            player.setInt(2, 77);
            player.setString(3, "Offline");
            player.setShort(4, (short) 44);
            player.setInt(5, 8);
            player.setString(6, "[0,123456,0]");
            player.executeUpdate();
        }
    }

    private static void verifyProjection(String databaseUrl, String user, String password) throws Exception {
        JdbcSocialProfileRepository repository = new JdbcSocialProfileRepository(
                () -> open(databaseUrl, user, password));
        SocialProfileRepository.StoredProfile stored = repository.findByPlayerId(9L);
        assertEquals("stored name", "Offline", stored.name());
        assertEquals("stored clan", "Clan", stored.clanName());
        assertEquals("stored raw power", 123_456L, stored.rawPower());
        assertEquals("stored activity", Instant.parse("2026-09-06T00:00:00Z"), stored.lastActivityAt());
        assertEquals("unknown profile", null, repository.findByPlayerId(10L));

        SocialProfileService.Profile profile = new SocialProfileService(repository)
                .loadOffline(9L, Instant.parse("2026-09-14T00:00:00Z"));
        assertTrue("offline profile stays offline", !profile.online());
        assertEquals("profile activity label", SocialProfileService.ACTIVITY_RECENT, profile.activityLabel());
    }

    private static Connection open(String url, String user, String password) throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    private static void dropGeneratedDatabase(String baseUrl, String user, String password, String databaseName)
            throws SQLException {
        if (!databaseName.matches("social_v2_profile_test_[0-9a-f]{32}")) {
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
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(label + " expected=" + expected + " actual=" + actual);
        }
    }
}
