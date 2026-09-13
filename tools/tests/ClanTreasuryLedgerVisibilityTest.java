package nro.models.clan;

/**
 * Regression checks for the player-facing clan contribution history.
 * This test is database-free: it verifies only which ledger records may reach
 * the client history panel.
 */
public final class ClanTreasuryLedgerVisibilityTest {

    public static void main(String[] args) {
        assertVisible("gold contribution", entry("DEPOSIT", ClanTreasuryService.CURRENCY_GOLD, 1L));
        assertVisible("gem contribution", entry("DEPOSIT", ClanTreasuryService.CURRENCY_GEM, 1L));
        assertHidden("capsule contribution", entry("DEPOSIT", ClanTreasuryService.CURRENCY_CAPSULE, 1L));
        assertHidden("non-deposit gold transaction", entry("SPEND", ClanTreasuryService.CURRENCY_GOLD, 1L));
        assertHidden("non-positive gold transaction", entry("DEPOSIT", ClanTreasuryService.CURRENCY_GOLD, 0L));
        System.out.println("ClanTreasuryLedgerVisibilityTest: PASS");
    }

    private static ClanTreasuryService.LedgerEntry entry(String actionType, byte currency, long amount) {
        return new ClanTreasuryService.LedgerEntry(1L, 2L, "player", actionType, currency, amount, 1L, 1L);
    }

    private static void assertVisible(String label, ClanTreasuryService.LedgerEntry entry) {
        if (!ClanTreasuryService.isPlayerContributionEntry(entry)) {
            throw new AssertionError(label + " should be visible");
        }
    }

    private static void assertHidden(String label, ClanTreasuryService.LedgerEntry entry) {
        if (ClanTreasuryService.isPlayerContributionEntry(entry)) {
            throw new AssertionError(label + " should be hidden");
        }
    }
}
