import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import nro.models.data.LocalManager;

/** Integration check for deployed phase 5E metric schema; probe writes are rolled back. */
public final class ClanPhase5EDatabaseTest {

    public static void main(String[] args) throws Exception {
        Set<String> tables = new HashSet<>();
        Set<String> columns = new HashSet<>();
        long metricRows;
        long activeClanDays;
        try (Connection connection = LocalManager.getConnection()) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT table_name,column_name FROM information_schema.columns "
                    + "WHERE table_schema=DATABASE() AND table_name IN (?,?)")) {
                ps.setString(1, "clan_economy_metric");
                ps.setString(2, "clan_economy_active_clan");
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        tables.add(rs.getString("table_name"));
                        columns.add(rs.getString("column_name").toLowerCase());
                    }
                }
            }
            assertTrue("daily metric table exists", tables.contains("clan_economy_metric"));
            assertTrue("active clan-day table exists", tables.contains("clan_economy_active_clan"));
            assertFalse("no player identity", columns.contains("player_id"));
            assertFalse("no actor identity", columns.contains("actor_id"));
            assertFalse("no request identity", columns.contains("request_id"));
            assertEquals("ledger report index", List.of("created_at", "currency_type", "action_type"),
                    indexColumns(connection, "clan_ledger", "idx_clan_ledger_economy"));

            verifyMetricUpsertCanRollback(connection);

            metricRows = scalar(connection, "SELECT COUNT(*) FROM clan_economy_metric");
            activeClanDays = scalar(connection, "SELECT COUNT(*) FROM clan_economy_active_clan");
        }
        System.out.println("ClanPhase5EDatabaseTest: PASS (metrics="
                + metricRows + ", activeClanDays=" + activeClanDays + ")");
    }

    private static List<String> indexColumns(Connection connection, String table, String index)
            throws Exception {
        List<String> result = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT column_name FROM information_schema.statistics "
                + "WHERE table_schema=DATABASE() AND table_name=? AND index_name=? ORDER BY seq_in_index")) {
            ps.setString(1, table);
            ps.setString(2, index);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(rs.getString(1).toLowerCase());
                }
            }
        }
        return result;
    }

    private static void verifyMetricUpsertCanRollback(Connection connection) throws Exception {
        boolean originalAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO clan_economy_metric(metric_day,signal_key,event_count,amount_total) "
                + "VALUES(CURRENT_DATE,?,?,?) ON DUPLICATE KEY UPDATE "
                + "event_count=IF(event_count>9223372036854775807-VALUES(event_count),"
                + "9223372036854775807,event_count+VALUES(event_count)),"
                + "amount_total=IF(amount_total>9223372036854775807-VALUES(amount_total),"
                + "9223372036854775807,amount_total+VALUES(amount_total))")) {
            ps.setString(1, "TEST_ONLY");
            ps.setLong(2, 1L);
            ps.setLong(3, 1L);
            ps.executeUpdate();
            connection.rollback();
        } catch (Exception ex) {
            connection.rollback();
            throw ex;
        } finally {
            connection.setAutoCommit(originalAutoCommit);
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COUNT(*) FROM clan_economy_metric WHERE signal_key=?")) {
            ps.setString(1, "TEST_ONLY");
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue("rolled-back metric probe has no residue", rs.next() && rs.getLong(1) == 0L);
            }
        }
    }

    private static long scalar(Connection connection, String sql) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) {
                throw new AssertionError("Missing scalar result");
            }
            return rs.getLong(1);
        }
    }

    private static void assertTrue(String label, boolean value) {
        if (!value) {
            throw new AssertionError(label);
        }
    }

    private static void assertFalse(String label, boolean value) {
        assertTrue(label, !value);
    }

    private static void assertEquals(String label, Object expected, Object actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError(label + " expected=" + expected + " actual=" + actual);
        }
    }
}
