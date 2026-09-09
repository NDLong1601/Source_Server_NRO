import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import nro.models.clan.Clan;
import nro.models.clan.ClanValueConfig;
import nro.models.data.LocalManager;

/** Read-only integration check for the phase-5B schema and startup materialization. */
public final class ClanPhase5BDatabaseTest {

    public static void main(String[] args) throws Exception {
        ClanValueConfig config = ClanValueConfig.load();
        int checked = 0;
        try (Connection connection = LocalManager.getConnection()) {
            assertColumn(connection, "clan_value");
            assertColumn(connection, "clan_value_version");
            assertColumn(connection, "clan_value_formula_version");
            assertColumn(connection, "clan_achievement_score");
            assertIndex(connection, "idx_clan_value");

            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT c.id,c.level,c.potential_total,c.potential_unspent,"
                    + "c.clan_achievement_score,c.clan_value,c.clan_value_version,"
                    + "c.clan_value_formula_version,c.tops,t.level AS tree_level "
                    + "FROM clan c LEFT JOIN clan_tree t ON t.clan_id=c.id")) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Clan clan = new Clan();
                        clan.id = rs.getInt("id");
                        clan.level = rs.getInt("level");
                        clan.potentialTotal = rs.getInt("potential_total");
                        clan.potentialUnspent = rs.getInt("potential_unspent");
                        clan.clanAchievementScore = rs.getLong("clan_achievement_score");
                        clan.loadWeeklyState(rs.getString("tops"));
                        clan.ensureWeeklyContract();
                        int spent = Math.max(0, clan.potentialTotal - clan.potentialUnspent);
                        ClanValueConfig.Score expected = config.score(clan.level, spent,
                                rs.getInt("tree_level"), clan.clanAchievementScore,
                                clan.weeklyContractProgress, clan.weeklyContractTarget);
                        assertEquals("materialized value for clan " + clan.id,
                                expected.totalValue(), rs.getLong("clan_value"));
                        assertEquals("formula version for clan " + clan.id,
                                expected.formulaVersion(), rs.getInt("clan_value_formula_version"));
                        assertTrue("value version for clan " + clan.id,
                                rs.getLong("clan_value_version") > 0L);
                        checked++;
                    }
                }
            }
        }
        System.out.println("ClanPhase5BDatabaseTest: PASS (clans=" + checked + ")");
    }

    private static void assertColumn(Connection connection, String column) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement("SHOW COLUMNS FROM clan LIKE ?")) {
            ps.setString(1, column);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue("missing column " + column, rs.next());
            }
        }
    }

    private static void assertIndex(Connection connection, String index) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement("SHOW INDEX FROM clan WHERE Key_name=?")) {
            ps.setString(1, index);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue("missing index " + index, rs.next());
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
