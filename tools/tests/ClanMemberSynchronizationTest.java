package nro.models.clan;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

/** Regression checks for member capsule synchronization and clan gift notices. */
public final class ClanMemberSynchronizationTest {

    public static void main(String[] args) {
        verifyTreeCapsuleCreditsClanContributionOnly();
        verifyTreeCapsuleContributionRejectsOverflowAtomically();
        verifyProjectedMemberPayloadMatchesCommittedReward();
        verifyGiftAnnouncementNamesBothPlayersAndReward();
        System.out.println("ClanMemberSynchronizationTest: PASS");
    }

    private static void verifyTreeCapsuleCreditsClanContributionOnly() {
        ClanMember member = new ClanMember();
        member.clanPoint = 7;
        member.memberPoint = 11;

        assertTrue("tree capsule contribution accepted", member.creditClanContribution(195));
        assertEquals("capsule contributed to clan", 202, member.clanPoint);
        assertEquals("personal capsule balance remains separate", 11, member.memberPoint);
    }

    private static void verifyTreeCapsuleContributionRejectsOverflowAtomically() {
        ClanMember member = new ClanMember();
        member.clanPoint = Integer.MAX_VALUE - 1;
        member.memberPoint = 20;

        assertFalse("overflow rejected", member.creditClanContribution(2));
        assertEquals("contribution unchanged after rejection", Integer.MAX_VALUE - 1, member.clanPoint);
        assertEquals("personal balance unchanged after rejection", 20, member.memberPoint);
    }

    private static void verifyProjectedMemberPayloadMatchesCommittedReward() {
        Clan clan = new Clan();
        ClanMember member = new ClanMember();
        member.id = 42;
        member.name = "harvester";
        member.clanPoint = 7;
        member.memberPoint = 11;
        clan.addClanMember(member);

        JSONArray members = (JSONArray) JSONValue.parse(clan.serializeMembersForPersistence(42, 195));
        JSONObject projected = (JSONObject) JSONValue.parse((String) members.get(0));

        assertEquals("persisted contribution projection", 202,
                ((Number) projected.get("clan_point")).intValue());
        assertEquals("persisted personal balance remains separate", 11,
                ((Number) projected.get("member_point")).intValue());
        assertEquals("projection leaves live contribution unchanged", 7, member.clanPoint);
        assertEquals("projection leaves live personal balance unchanged", 11, member.memberPoint);
    }

    private static void verifyGiftAnnouncementNamesBothPlayersAndReward() {
        assertEquals("gift announcement",
                "player1 đã tặng quà bang cho player2: 250000 vàng.",
                ClanGiftService.giftAnnouncement("player1", "player2", "250000 vàng"));
    }

    private static void assertEquals(String label, int expected, int actual) {
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
