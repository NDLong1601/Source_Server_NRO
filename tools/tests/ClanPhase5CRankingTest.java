import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import nro.models.clan.ClanFeatureFlags;
import nro.models.clan.ClanRankingConfig;
import nro.models.clan.ClanRankingPolicy;
import nro.models.clan.ClanRankingProtocol;

/** Pure regression checks for phase-5C ranking, pagination and packet bounds. */
public final class ClanPhase5CRankingTest {

    public static void main(String[] args) throws Exception {
        verifyConfigurationBounds();
        verifyStableRankingAndPagination();
        verifyProtocolRoundTripAndCeiling();
        verifyDeployedRollout();
        System.out.println("ClanPhase5CRankingTest: PASS");
    }

    private static void verifyConfigurationBounds() {
        Properties values = new Properties();
        values.setProperty("default_page_size", "20");
        values.setProperty("max_page_size", "40");
        values.setProperty("max_page", "100");
        values.setProperty("refresh_seconds", "15");
        values.setProperty("legacy_top_size", "30");
        ClanRankingConfig config = ClanRankingConfig.from(values);

        assertEquals("negative page", 0, config.normalizePage(-1));
        assertEquals("page cap", 100, config.normalizePage(65_535));
        assertEquals("default page size", 20, config.normalizePageSize(0));
        assertEquals("page size cap", 40, config.normalizePageSize(255));
        assertEquals("refresh millis", 15_000L, config.refreshMillis());
        assertEquals("legacy top size", 30, config.legacyTopSize());
    }

    private static void verifyStableRankingAndPagination() {
        ClanRankingConfig config = ClanRankingConfig.from(new Properties());
        List<ClanRankingPolicy.Candidate> sorted = ClanRankingPolicy.sorted(List.of(
                candidate(8, 9_000L, 99, 20, 1_000L),
                candidate(5, 10_000L, 5, 4, 5_000L),
                candidate(4, 10_000L, 5, 4, 4_000L),
                candidate(3, 10_000L, 5, 5, 9_000L),
                candidate(2, 10_000L, 6, 1, 9_000L),
                candidate(1, 10_000L, 5, 4, 4_000L)));

        assertOrder(sorted, 2, 3, 1, 4, 5, 8);
        ClanRankingPolicy.Page page = ClanRankingPolicy.page(sorted, 4, 1, 2, config);
        assertEquals("normalized page", 1, page.pageNumber());
        assertEquals("page size", 2, page.pageSize());
        assertEquals("total entries", 6, page.totalEntries());
        assertEquals("total pages", 3, page.totalPages());
        assertEquals("requester rank", 4, page.requesterRank());
        assertOrder(page.entries(), 1, 4);

        ClanRankingPolicy.Page pastEnd = ClanRankingPolicy.page(sorted, -1, 99, 2, config);
        assertEquals("past-end page empty", 0, pastEnd.entries().size());
        assertEquals("no requester clan", 0, pastEnd.requesterRank());
    }

    private static void verifyProtocolRoundTripAndCeiling() throws Exception {
        ClanRankingConfig config = ClanRankingConfig.from(new Properties());
        List<ClanRankingPolicy.Candidate> entries = new ArrayList<>();
        String longName = "\u0800".repeat(255);
        for (int i = 0; i < 50; i++) {
            entries.add(new ClanRankingPolicy.Candidate(i + 1, longName, "ABCD", i,
                    1_000 - i, 20, 50, 50, Long.MAX_VALUE - i, i + 1L,
                    1_000L + i, 10_000 + i, 1, 2, 3));
        }
        ClanRankingPolicy.Page page = ClanRankingPolicy.page(entries, 25, 0, 50, config);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream writer = new DataOutputStream(bytes)) {
            ClanRankingProtocol.write(writer, page, 7L, 123_456L);
        }
        assertTrue("ranking packet ceiling", bytes.size() <= 65_535);

        try (DataInputStream reader = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            ClanRankingProtocol.Snapshot decoded = ClanRankingProtocol.read(reader);
            assertEquals("ranking action", 127, decoded.action());
            assertEquals("ranking detail version", 1, decoded.version());
            assertEquals("decoded board version", 7L, decoded.boardVersion());
            assertEquals("decoded generated at", 123_456L, decoded.generatedAt());
            assertEquals("decoded rows", 50, decoded.entries().size());
            assertEquals("decoded requester rank", 25, decoded.requesterRank());
            assertEquals("decoded first rank", 1, decoded.entries().get(0).rank());
            assertEquals("decoded last clan", 50, decoded.entries().get(49).clanId());
        }
    }

    private static void verifyDeployedRollout() {
        ClanFeatureFlags flags = ClanFeatureFlags.load(Path.of("data", "clan_features.properties"));
        assertTrue("phase 5C ranking enabled", flags.isEnabled(ClanFeatureFlags.Feature.RANKING));
        assertFalse("phase 5C ranking is read-only", flags.canMutate(ClanFeatureFlags.Feature.RANKING));
        assertTrue("phase 5D appearance enabled after rollout", flags.isEnabled(ClanFeatureFlags.Feature.APPEARANCE));
        assertFalse("phase 5D appearance remains automatic/read-only",
                flags.canMutate(ClanFeatureFlags.Feature.APPEARANCE));
    }

    private static ClanRankingPolicy.Candidate candidate(int id, long value, int level,
            int treeLevel, long createdAt) {
        return new ClanRankingPolicy.Candidate(id, "Clan " + id, "C" + id, id,
                level, treeLevel, 1, 10, value, id, createdAt,
                100 + id, 1, 2, 3);
    }

    private static void assertOrder(List<ClanRankingPolicy.Candidate> values, int... ids) {
        assertEquals("order length", ids.length, values.size());
        for (int i = 0; i < ids.length; i++) {
            assertEquals("order[" + i + "]", ids[i], values.get(i).clanId());
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

    private static void assertFalse(String label, boolean value) {
        assertTrue(label, !value);
    }
}
