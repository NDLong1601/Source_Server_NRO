import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import javax.imageio.ImageIO;
import nro.models.clan.ClanAppearanceConfig;
import nro.models.clan.ClanAppearancePolicy;
import nro.models.clan.ClanAppearanceProtocol;
import nro.models.clan.ClanFeatureFlags;

/** Pure regression checks for phase-5D cosmetic unlocks and protocol bounds. */
public final class ClanPhase5DAppearanceTest {

    public static void main(String[] args) throws Exception {
        verifyAllUnlockRequirementsAndStableRevision();
        verifyConfigurationSanitization();
        verifyProtocolRoundTripAndCeiling();
        verifyDeployedRolloutAndAssets();
        System.out.println("ClanPhase5DAppearanceTest: PASS");
    }

    private static void verifyAllUnlockRequirementsAndStableRevision() {
        ClanAppearanceConfig config = configuredTiers();

        assertEquals("clan level gate", 0,
                ClanAppearancePolicy.resolve(config, 19, 20, 10_000L, 7L).tier().id());
        assertEquals("tree level gate", 0,
                ClanAppearancePolicy.resolve(config, 20, 9, 10_000L, 7L).tier().id());
        assertEquals("clan value gate", 0,
                ClanAppearancePolicy.resolve(config, 20, 10, 99L, 7L).tier().id());

        ClanAppearancePolicy.Appearance tierOne =
                ClanAppearancePolicy.resolve(config, 20, 10, 100L, 7L);
        assertEquals("tier one unlocked", 1, tierOne.tier().id());
        assertEquals("tree resource", "cay_lv_10", tierOne.resourceName());
        assertEquals("next tier", 2, tierOne.nextTier().id());
        assertEquals("remaining clan levels", 20, tierOne.remainingClanLevels());
        assertEquals("remaining tree levels", 5, tierOne.remainingTreeLevels());
        assertEquals("remaining clan value", 100L, tierOne.remainingClanValue());

        ClanAppearancePolicy.Appearance max =
                ClanAppearancePolicy.resolve(config, 999, 999, Long.MAX_VALUE, 9L);
        assertEquals("highest tier", 2, max.tier().id());
        assertEquals("resource level cap", "cay_lv_20", max.resourceName());
        assertTrue("last tier has no next tier", max.nextTier() == null);
        assertEquals("last tier value remainder", 0L, max.remainingClanValue());

        long revision = tierOne.visualRevision();
        assertEquals("visual revision deterministic", revision,
                ClanAppearancePolicy.resolve(config, 20, 10, 100L, 7L).visualRevision());
        assertTrue("value version invalidates appearance",
                revision != ClanAppearancePolicy.resolve(config, 20, 10, 100L, 8L).visualRevision());
    }

    private static void verifyConfigurationSanitization() {
        Properties values = new Properties();
        values.setProperty("resource_prefix", "../../bad/path");
        values.setProperty("max_resource_level", "999");
        values.setProperty("tier_0_name", "A\u0000\nB");
        values.setProperty("tier_1_value", "-5");
        values.setProperty("tier_1_clan_level", "-1");
        values.setProperty("tier_1_tree_level", "-1");
        ClanAppearanceConfig config = ClanAppearanceConfig.from(values);

        assertEquals("unsafe prefix falls back", "cay_lv_01", config.resourceName(-10));
        assertEquals("resource cap", "cay_lv_20", config.resourceName(999));
        assertFalse("control characters removed", config.tier(0).name().contains("\n"));
        assertTrue("tier clan values monotonic",
                config.tier(1).minimumClanValue() >= config.tier(0).minimumClanValue());
        assertTrue("tier clan levels monotonic",
                config.tier(1).minimumClanLevel() >= config.tier(0).minimumClanLevel());
        assertTrue("tier tree levels monotonic",
                config.tier(1).minimumTreeLevel() >= config.tier(0).minimumTreeLevel());
    }

    private static void verifyProtocolRoundTripAndCeiling() throws Exception {
        ClanAppearancePolicy.Appearance appearance =
                ClanAppearancePolicy.resolve(configuredTiers(), 20, 10, 100L, 7L);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream writer = new DataOutputStream(bytes)) {
            ClanAppearanceProtocol.write(writer, 42, true, appearance);
        }
        assertTrue("appearance packet ceiling", bytes.size() <= 65_535);

        try (DataInputStream reader = new DataInputStream(
                new ByteArrayInputStream(bytes.toByteArray()))) {
            ClanAppearanceProtocol.Snapshot decoded = ClanAppearanceProtocol.read(reader);
            assertEquals("appearance action", 128, decoded.action());
            assertEquals("appearance protocol version", 1, decoded.version());
            assertEquals("appearance clan", 42, decoded.clanId());
            assertTrue("appearance enabled", decoded.enabled());
            assertEquals("appearance tier", 1, decoded.tierId());
            assertEquals("appearance tier count", 3, decoded.tierCount());
            assertEquals("appearance resource", "cay_lv_10", decoded.resourceName());
            assertEquals("appearance value", 100L, decoded.clanValue());
            assertEquals("appearance value version", 7L, decoded.clanValueVersion());
            assertEquals("appearance next tier", 2, decoded.nextTierId());
            assertEquals("appearance remaining value", 100L, decoded.remainingClanValue());
        }
    }

    private static void verifyDeployedRolloutAndAssets() throws Exception {
        ClanFeatureFlags flags = ClanFeatureFlags.load(Path.of("data", "clan_features.properties"));
        assertTrue("phase 5D appearance enabled", flags.isEnabled(ClanFeatureFlags.Feature.APPEARANCE));
        assertFalse("phase 5D is automatic/read-only",
                flags.canMutate(ClanFeatureFlags.Feature.APPEARANCE));
        ClanAppearanceConfig config = ClanAppearanceConfig.load();
        assertEquals("deployed tier count", 4, config.tierCount());
        assertEquals("first cosmetic milestone", 20, config.tier(1).minimumClanLevel());
        for (int level = 1; level <= 20; level++) {
            int baseWidth = 0;
            int baseHeight = 0;
            for (int zoom = 1; zoom <= 4; zoom++) {
                Path asset = Path.of("data", "img_by_name", "x" + zoom,
                        config.resourceName(level) + ".png");
                assertTrue("tree asset exists: " + asset, Files.isRegularFile(asset));
                var image = ImageIO.read(asset.toFile());
                assertTrue("tree asset is readable: " + asset,
                        image != null && image.getWidth() > 0 && image.getHeight() > 0);
                assertTrue("tree asset keeps alpha: " + asset,
                        image.getColorModel().hasAlpha() && ((image.getRGB(0, 0) >>> 24) & 0xFF) == 0);
                if (zoom == 1) {
                    baseWidth = image.getWidth();
                    baseHeight = image.getHeight();
                } else {
                    assertEquals("tree asset width follows zoom: " + asset,
                            baseWidth * zoom, image.getWidth());
                    assertEquals("tree asset height follows zoom: " + asset,
                            baseHeight * zoom, image.getHeight());
                }
            }
        }
    }

    private static ClanAppearanceConfig configuredTiers() {
        Properties values = new Properties();
        values.setProperty("tier_count", "3");
        values.setProperty("tier_0_value", "0");
        values.setProperty("tier_0_clan_level", "1");
        values.setProperty("tier_0_tree_level", "1");
        values.setProperty("tier_1_value", "100");
        values.setProperty("tier_1_clan_level", "20");
        values.setProperty("tier_1_tree_level", "10");
        values.setProperty("tier_2_value", "200");
        values.setProperty("tier_2_clan_level", "40");
        values.setProperty("tier_2_tree_level", "15");
        return ClanAppearanceConfig.from(values);
    }

    private static void assertEquals(String label, long expected, long actual) {
        if (expected != actual) {
            throw new AssertionError(label + ": expected=" + expected + ", actual=" + actual);
        }
    }

    private static void assertEquals(String label, String expected, String actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError(label + ": expected=" + expected + ", actual=" + actual);
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
}
