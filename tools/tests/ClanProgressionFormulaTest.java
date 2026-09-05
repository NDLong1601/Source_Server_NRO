import nro.models.clan.ClanProgressionService;

/** Regression checks for the phase-3 formulas and points backfill contract. */
public final class ClanProgressionFormulaTest {

    public static void main(String[] args) {
        ClanProgressionService progression = ClanProgressionService.gI();
        assertEquals("level 1 points", 0, progression.totalPointsForLevel(1));
        assertEquals("level 2 points", 5, progression.totalPointsForLevel(2));
        assertEquals("level 10 points", 45, progression.totalPointsForLevel(10));
        assertEquals("level 1000 points", 4_995, progression.totalPointsForLevel(1_000));
        assertEquals("level 1 member slots", 10, progression.memberSlotsForLevel(1));
        assertEquals("level 5 member slots", 14, progression.memberSlotsForLevel(5));
        assertEquals("level 20 member slots", 29, progression.memberSlotsForLevel(20));
        assertEquals("member slot cap", 50, progression.memberSlotsForLevel(41));
        assertEquals("member slot cap at technical max", 50, progression.memberSlotsForLevel(1_000));
        assertEquals("rank 0 cost", 1, progression.costForRank(0));
        assertEquals("rank 9 cost", 1, progression.costForRank(9));
        assertEquals("rank 10 cost", 1, progression.costForRank(10));
        assertEquals("rank 20 cost", 1, progression.costForRank(20));
        assertEquals("attack 1 point is 0.2%", 20,
                progression.effectBasisPoints(ClanProgressionService.Branch.ATTACK, 1));
        assertEquals("attack 5 points is 1%", 100,
                progression.effectBasisPoints(ClanProgressionService.Branch.ATTACK, 5));
        assertEquals("attack 6 points is 1.2%", 120,
                progression.effectBasisPoints(ClanProgressionService.Branch.ATTACK, 6));
        assertEquals("hp 7 points is 1.4%", 140,
                progression.effectBasisPoints(ClanProgressionService.Branch.HP, 7));
        assertEquals("ki 20 points is 4%", 400,
                progression.effectBasisPoints(ClanProgressionService.Branch.KI, 20));
        assertEquals("luck 1 point is 1%", 100,
                progression.effectBasisPoints(ClanProgressionService.Branch.LUCK, 1));
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
