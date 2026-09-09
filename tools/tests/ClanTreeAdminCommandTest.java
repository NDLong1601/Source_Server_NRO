import nro.models.clan.ClanTreeAdminCommand;

/** Pure input-boundary checks for the in-game admin tree-level command. */
public final class ClanTreeAdminCommandTest {

    public static void main(String[] args) {
        acceptsConfiguredEndpointsAndIntermediateLevels();
        rejectsMissingMalformedAndOutOfRangeLevels();
        System.out.println("ClanTreeAdminCommandTest: PASS");
    }

    private static void acceptsConfiguredEndpointsAndIntermediateLevels() {
        assertAccepted("minimum", "1", 20, 1);
        assertAccepted("trimmed", " 10 ", 20, 10);
        assertAccepted("maximum", "20", 20, 20);
    }

    private static void rejectsMissingMalformedAndOutOfRangeLevels() {
        assertRejected("null", null, 20);
        assertRejected("blank", "   ", 20);
        assertRejected("text", "cap10", 20);
        assertRejected("decimal", "10.5", 20);
        assertRejected("below minimum", "0", 20);
        assertRejected("negative", "-1", 20);
        assertRejected("above configured maximum", "21", 20);
        assertRejected("integer overflow", "999999999999999999999", 20);
        assertRejected("invalid configured maximum", "1", 0);
    }

    private static void assertAccepted(String label, String raw, int maxLevel, int expectedLevel) {
        ClanTreeAdminCommand.LevelInput result = ClanTreeAdminCommand.parseLevel(raw, maxLevel);
        if (!result.accepted() || result.level() != expectedLevel || !result.message().isEmpty()) {
            throw new AssertionError(label + ": expected accepted level " + expectedLevel + ", got " + result);
        }
    }

    private static void assertRejected(String label, String raw, int maxLevel) {
        ClanTreeAdminCommand.LevelInput result = ClanTreeAdminCommand.parseLevel(raw, maxLevel);
        if (result.accepted() || result.message().isBlank()) {
            throw new AssertionError(label + ": expected a rejection with a user-facing message, got " + result);
        }
    }
}
