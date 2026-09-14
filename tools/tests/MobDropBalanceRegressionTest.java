package nro.models.mob;

/** Regression coverage for the configured normal-mob loot economy. */
public final class MobDropBalanceRegressionTest {

    private MobDropBalanceRegressionTest() {
    }

    public static void main(String[] args) {
        assertRate("training-map 5-7 star dragon balls", 5, 100,
                Mob.DRAGON_BALL_TRAIN_DROP_BASIS_POINTS,
                Mob.DRAGON_BALL_TRAIN_DROP_DENOMINATOR);
        assertRate("dungeon 4 star dragon balls", 5, 100,
                Mob.DRAGON_BALL_GENERAL_DROP_BASIS_POINTS,
                Mob.DRAGON_BALL_GENERAL_DROP_DENOMINATOR);
        assertRate("crystal stars with detector", 100, 10_000,
                Mob.CRYSTAL_STAR_DETECTOR_DROP_BASIS_POINTS,
                Mob.CRYSTAL_STAR_DETECTOR_DROP_DENOMINATOR);
        System.out.println("MOB_DROP_BALANCE_REGRESSION_TEST_OK");
    }

    private static void assertRate(String label, int expectedNumerator, int expectedDenominator,
            int actualNumerator, int actualDenominator) {
        if (actualNumerator != expectedNumerator || actualDenominator != expectedDenominator) {
            throw new AssertionError(label + " must be " + expectedNumerator + "/" + expectedDenominator
                    + ", actual=" + actualNumerator + "/" + actualDenominator);
        }
    }
}
