import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashSet;
import java.util.Set;
import nro.models.clan.ClanAppearanceConfig;
import nro.models.clan.ClanAppearancePolicy;
import nro.models.data.LocalManager;

/** Read-only integration check for deployed tree resources and live clan appearance inputs. */
public final class ClanPhase5DDatabaseTest {

    public static void main(String[] args) throws Exception {
        ClanAppearanceConfig config = ClanAppearanceConfig.load();
        Set<String> resources = new HashSet<>();
        int clans = 0;
        try (Connection connection = LocalManager.getConnection()) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT name,n_frame FROM img_by_name WHERE name LIKE ?")) {
                ps.setString(1, "cay\\_lv\\_%");
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        assertTrue("tree image frame count", rs.getInt("n_frame") > 0);
                        resources.add(rs.getString("name"));
                    }
                }
            }
            for (int level = 1; level <= 20; level++) {
                assertTrue("registered tree resource level " + level,
                        resources.contains(config.resourceName(level)));
            }

            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT c.id,c.level,c.clan_value,c.clan_value_version,"
                    + "COALESCE(t.level,1) AS tree_level FROM clan c "
                    + "LEFT JOIN clan_tree t ON t.clan_id=c.id")) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        ClanAppearancePolicy.Appearance appearance = ClanAppearancePolicy.resolve(config,
                                rs.getInt("level"), rs.getInt("tree_level"),
                                rs.getLong("clan_value"), rs.getLong("clan_value_version"));
                        assertTrue("valid appearance tier for clan " + rs.getInt("id"),
                                appearance.tier().id() >= 0
                                && appearance.tier().id() < config.tierCount());
                        assertTrue("registered appearance resource for clan " + rs.getInt("id"),
                                resources.contains(appearance.resourceName()));
                        clans++;
                    }
                }
            }
        }
        System.out.println("ClanPhase5DDatabaseTest: PASS (resources="
                + resources.size() + ", clans=" + clans + ")");
    }

    private static void assertTrue(String label, boolean value) {
        if (!value) {
            throw new AssertionError(label);
        }
    }
}
