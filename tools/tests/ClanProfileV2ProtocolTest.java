import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.List;
import nro.models.clan.ClanProfileV2;
import nro.models.clan.ClanBuffService;
import nro.models.clan.ClanTreasuryService;

/**
 * Lightweight protocol regression test for clan level/member values that do
 * not fit in the legacy byte fields.
 */
public final class ClanProfileV2ProtocolTest {

    public static void main(String[] args) throws Exception {
        verifyRoundTrip(17, 127, 255, 50);
        verifyRoundTrip(17, 128, 256, 50);
        verifyRoundTrip(17, 1_000, 50, 50);
        System.out.println("ClanProfileV2ProtocolTest: PASS");
    }

    private static void verifyRoundTrip(int clanId, int level, int maxMember,
            int currentMember) throws Exception {
        long clanGold = 1_010_190_000L;
        long clanGem = 2_000L;
        long treasuryVersion = 5L;
        long memberContribution = 3_010_190_000L;
        long clanExp = 110L;
        long expRequired = 500L;
        int capsuleRequired = 20;
        long goldRequired = 2_000_000_000L;
        long gemRequired = 2_500L;
        int potentialTotal = 7;
        int potentialUnspent = 2;
        long progressionVersion = 9L;
        int[] potentialRanks = {5, 4, 3, 2, 1, 0};
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream writer = new DataOutputStream(bytes)) {
            ClanProfileV2.write(writer, clanId, level, maxMember, currentMember,
                    clanGold, clanGem, treasuryVersion, memberContribution,
                    List.of(new ClanTreasuryService.LedgerEntry(9L, 1367L, "LONG", "DEPOSIT",
                            (byte) 1, 20_000_000L, clanGold, 1_788_500_000L)),
                    clanExp, expRequired, capsuleRequired, goldRequired, gemRequired,
                    potentialTotal, potentialUnspent, progressionVersion, potentialRanks,
                    List.of(new ClanBuffService.BuffView(2, 10,
                            System.currentTimeMillis() + 86_400_000L)));
        }

        try (DataInputStream reader = new DataInputStream(
                new ByteArrayInputStream(bytes.toByteArray()))) {
            ClanProfileV2.Profile profile = ClanProfileV2.read(reader);
            assertEquals("clanId", clanId, profile.clanId);
            assertEquals("attack buff percent", 10, profile.buffPercents[2]);
            assertTrue("attack buff remaining", profile.buffRemainingMillis[2] > 86_300_000L);
            assertEquals("level", level, profile.level);
            assertEquals("maxMember", maxMember, profile.maxMember);
            assertEquals("currentMember", currentMember, profile.currentMember);
            assertEquals("clanGold", clanGold, profile.clanGold);
            assertEquals("clanGem", clanGem, profile.clanGem);
            assertEquals("treasuryVersion", treasuryVersion, profile.treasuryVersion);
            assertEquals("memberContribution", memberContribution, profile.memberContribution);
            assertEquals("ledgerCount", 1, profile.ledgerEntries.size());
            assertEquals("ledgerAmount", 20_000_000L, profile.ledgerEntries.get(0).amount());
            assertEquals("clanExp", clanExp, profile.clanExp);
            assertEquals("expRequired", expRequired, profile.expRequired);
            assertEquals("capsuleRequired", capsuleRequired, profile.capsuleRequired);
            assertEquals("goldRequired", goldRequired, profile.goldRequired);
            assertEquals("gemRequired", gemRequired, profile.gemRequired);
            assertEquals("potentialTotal", potentialTotal, profile.potentialTotal);
            assertEquals("potentialUnspent", potentialUnspent, profile.potentialUnspent);
            assertEquals("progressionVersion", progressionVersion, profile.progressionVersion);
            for (int i = 0; i < potentialRanks.length; i++) {
                assertEquals("potentialRank[" + i + "]", potentialRanks[i], profile.potentialRanks[i]);
            }
        }
    }

    private static void assertEquals(String field, int expected, int actual) {
        if (expected != actual) {
            throw new AssertionError(field + ": expected=" + expected + ", actual=" + actual);
        }
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
