package nro.models.social;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Static regression checks for the additive schema and JDBC safety boundary. */
public final class SocialPersistenceSchemaContractTest {

    private SocialPersistenceSchemaContractTest() {
    }

    public static void main(String[] args) throws Exception {
        Path root = Path.of("").toAbsolutePath().normalize();
        String migration = read(root, "sql/migrations/20260914_add_social_v2_friendship.sql");
        String jdbc = read(root, "src/nro/models/social/JdbcSocialRelationshipRepository.java");
        String friendship = read(root, "src/nro/models/social/FriendshipRepository.java");
        String requests = read(root, "src/nro/models/social/FriendRequestRepository.java");
        String backfill = read(root, "src/nro/models/social/LegacyFriendBackfill.java");

        require("friendship table", migration, "CREATE TABLE IF NOT EXISTS player_friendship");
        require("friendship canonical primary key", migration, "PRIMARY KEY (player_low_id, player_high_id)");
        require("friendship reverse index", migration, "idx_player_friendship_high");
        require("pending request table", migration, "CREATE TABLE IF NOT EXISTS friend_request");
        require("one pending request per pair", migration, "uk_friend_request_pair");
        require("request expiry index", migration, "idx_friend_request_expiry");
        require("MariaDB-compatible expiry column", migration, "expires_at DATETIME NOT NULL");
        require("InnoDB", migration, "ENGINE=InnoDB");
        reject("legacy JSON remains", migration, "DROP COLUMN friends");
        reject("no chat persistence", migration, "chat_message");

        require("transaction disabled autocommit", jdbc, "connection.setAutoCommit(false)");
        require("canonical first player lock", jdbc, "lockPlayer(pair.lowPlayerId())");
        require("canonical second player lock", jdbc, "lockPlayer(pair.highPlayerId())");
        require("row locks", jdbc, "SELECT id FROM player WHERE id=? FOR UPDATE");
        require("rollback", jdbc, "rollbackQuietly(connection)");
        require("friendship parameters", friendship, "player_low_id=? AND player_high_id=?");
        require("request parameters", requests, "pair_low_id=? AND pair_high_id=? FOR UPDATE");
        require("request id lock", requests, "WHERE id=? FOR UPDATE");
        require("backfill apply guard", backfill, "social_v2.migration_enabled");
        require("backfill idempotency", backfill, "ON DUPLICATE KEY UPDATE player_low_id=VALUES(player_low_id)");
        reject("backfill must not hide errors", backfill, "INSERT IGNORE");
        System.out.println("SocialPersistenceSchemaContractTest: PASS");
    }

    private static String read(Path root, String relative) throws Exception {
        Path path = root.resolve(relative);
        if (!Files.isRegularFile(path)) {
            throw new AssertionError("Missing required Social V2 file: " + path);
        }
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private static void require(String label, String source, String expected) {
        if (!source.contains(expected)) {
            throw new AssertionError(label + " missing: " + expected);
        }
    }

    private static void reject(String label, String source, String unexpected) {
        if (source.contains(unexpected)) {
            throw new AssertionError(label + " must not contain: " + unexpected);
        }
    }
}
