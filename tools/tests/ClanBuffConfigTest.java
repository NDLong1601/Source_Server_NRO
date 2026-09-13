import java.util.Properties;
import nro.models.clan.ClanBuffConfig;

/** Pure configuration checks for clan-wide temporary buffs. */
public final class ClanBuffConfigTest {

    public static void main(String[] args) {
        verifyDefaults();
        verifyCustomValuesAndBounds();
        System.out.println("ClanBuffConfigTest: PASS");
    }

    private static void verifyDefaults() {
        ClanBuffConfig config = ClanBuffConfig.from(new Properties());
        assertEquals("HP recovery percent", 1, config.effectPercent(0));
        assertEquals("KI recovery percent", 1, config.effectPercent(1));
        assertEquals("attack percent", 10, config.effectPercent(2));
        assertEquals("luck percent", 10, config.effectPercent(3));
        assertEquals("power percent", 15, config.effectPercent(4));
        assertEquals("mob gold percent", 20, config.effectPercent(5));
        assertEquals("short duration", 1, config.durationDaysForItem(2252));
        assertEquals("medium duration", 3, config.durationDaysForItem(2253));
        assertEquals("long duration", 7, config.durationDaysForItem(2254));
        assertEquals("default recovery interval", 5_000L, config.recoveryIntervalMillis());
    }

    private static void verifyCustomValuesAndBounds() {
        Properties values = new Properties();
        values.setProperty("attack_percent", "25");
        values.setProperty("mob_gold_percent", "101");
        values.setProperty("short_duration_days", "2");
        values.setProperty("medium_duration_days", "4");
        values.setProperty("long_duration_days", "9");
        values.setProperty("recovery_interval_seconds", "0");
        ClanBuffConfig config = ClanBuffConfig.from(values);
        assertEquals("custom attack percent", 25, config.effectPercent(2));
        assertEquals("buff percent capped", 100, config.effectPercent(5));
        assertEquals("custom short duration", 2, config.durationDaysForItem(2261));
        assertEquals("custom medium duration", 4, config.durationDaysForItem(2262));
        assertEquals("custom long duration", 9, config.durationDaysForItem(2263));
        assertEquals("recovery lower bound", 1_000L, config.recoveryIntervalMillis());
        assertEquals("non-buff item", 0, config.durationDaysForItem(2270));
    }

    private static void assertEquals(String label, long expected, long actual) {
        if (expected != actual) {
            throw new AssertionError(label + ": expected=" + expected + ", actual=" + actual);
        }
    }
}
