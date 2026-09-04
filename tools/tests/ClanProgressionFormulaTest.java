import nro.models.clan.ClanProgressionService;

/** Regression checks for the phase-3 formulas and points backfill contract. */
public final class ClanProgressionFormulaTest {

    public static void main(String[] args) {
        ClanProgressionService progression = ClanProgressionService.gI();
        assertEquals("level 1 points", 0, progression.totalPointsForLevel(1));
        assertEquals("level 2 points", 5, progression.totalPointsForLevel(2));
        assertEquals("level 10 points", 45, progression.totalPointsForLevel(10));
        assertEquals("level 1000 points", 4_995, progression.totalPointsForLevel(1_000));
        assertEquals("rank 0 cost", 1, progression.costForRank(0));
        assertEquals("rank 9 cost", 1, progression.costForRank(9));
        assertEquals("rank 10 cost", 2, progression.costForRank(10));
        assertEquals("rank 20 cost", 3, progression.costForRank(20));
        assertTrue("EXP grows", progression.expRequired(10) > progression.expRequired(1));
        assertTrue("capsule grows", progression.capsuleRequired(10) > progression.capsuleRequired(1));
        assertEquals("gold starts at level 5", 0L, progression.goldRequired(4));
        assertTrue("gold required at level 5", progression.goldRequired(5) > 0L);
        assertEquals("gem before milestone", 0L, progression.gemRequired(8));
        assertTrue("gem at level-10 upgrade", progression.gemRequired(9) > 0L);
        System.out.println("ClanProgressionFormulaTest: PASS");
    }

    private static void assertEquals(String field, long expected, long actual) {
        if (expected != actual) {
            throw new AssertionError(field + ": expected=" + expected + ", actual=" + actual);
        }
    }

    private static void assertTrue(String field, boolean value) {
        if (!value) {
            throw new AssertionError(field);
        }
    }
}
