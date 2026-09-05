import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import nro.models.clan.Clan;
import nro.models.clan.ClanBuffService;
import nro.models.data.LocalManager;

/** Transactional regression test for temporary clan-buff duration stacking. */
public final class ClanBuffServiceTest {

    private static final int TEST_CLAN_ID = 2_000_000_000;

    public static void main(String[] args) throws Exception {
        Clan clan = new Clan();
        clan.id = TEST_CLAN_ID;
        try (Connection connection = LocalManager.getConnection()) {
            ClanBuffService.gI().ensureSchema(connection);
            connection.setAutoCommit(false);
            try {
                deleteFixture(connection);
                ClanBuffService.Activation first = ClanBuffService.gI()
                        .activateInTransaction(connection, clan, 1L, 2252);
                ClanBuffService.Activation second = ClanBuffService.gI()
                        .activateInTransaction(connection, clan, 1L, 2253);
                assertTrue("2252 activation", first.success());
                assertTrue("2253 activation", second.success());
                assertEquals("same buff type", first.type().wireId, second.type().wireId);
                assertEquals("duration adds three days", first.expiresAt() + 3L * ClanBuffService.DAY_MILLIS,
                        second.expiresAt());
                assertRow(connection, first.type().wireId, 20, second.expiresAt());

                ClanBuffService.Activation attack = ClanBuffService.gI()
                        .activateInTransaction(connection, clan, 1L, 2267);
                assertTrue("2267 activation", attack.success());
                assertRow(connection, attack.type().wireId, 10, attack.expiresAt());
                assertEquals("two independent buff rows", 2L, countRows(connection));
                connection.commit();
                List<ClanBuffService.BuffView> snapshot = ClanBuffService.gI().buffSnapshot(TEST_CLAN_ID);
                assertEquals("snapshot always carries six states", 6L, snapshot.size());
                assertEquals("two active states", 2L, snapshot.stream()
                        .filter(buff -> buff.expiresAt() > System.currentTimeMillis()).count());
                connection.setAutoCommit(false);
                System.out.println("ClanBuffServiceTest: PASS");
            } finally {
                connection.rollback();
                deleteFixture(connection);
                connection.commit();
                connection.setAutoCommit(true);
            }
        } finally {
            LocalManager.close();
        }
    }

    private static void deleteFixture(Connection connection) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM clan_active_buff_audit WHERE clan_id=?")) {
            ps.setInt(1, TEST_CLAN_ID);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM clan_active_buff WHERE clan_id=?")) {
            ps.setInt(1, TEST_CLAN_ID);
            ps.executeUpdate();
        }
    }

    private static void assertRow(Connection connection, int type, int percent, long expiresAt) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT effect_percent,expires_at FROM clan_active_buff WHERE clan_id=? AND buff_type=?")) {
            ps.setInt(1, TEST_CLAN_ID);
            ps.setInt(2, type);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue("buff row exists", rs.next());
                assertEquals("effect percent", percent, rs.getLong(1));
                assertEquals("expiry", expiresAt, rs.getLong(2));
            }
        }
    }

    private static long countRows(Connection connection) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COUNT(*) FROM clan_active_buff WHERE clan_id=?")) {
            ps.setInt(1, TEST_CLAN_ID);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private static void assertEquals(String label, long expected, long actual) {
        if (expected != actual) {
            throw new AssertionError(label + ": expected=" + expected + ", actual=" + actual);
        }
    }

    private static void assertTrue(String label, boolean value) {
        if (!value) {
            throw new AssertionError(label);
        }
    }
}
