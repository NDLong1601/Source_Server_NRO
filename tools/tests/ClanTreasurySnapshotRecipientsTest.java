package nro.models.clan;

import java.util.Arrays;
import java.util.List;

/** Regression checks for recipients of shared clan-currency snapshots. */
public final class ClanTreasurySnapshotRecipientsTest {

    public static void main(String[] args) {
        String onlineMember = "clan:101";
        String staleMember = "other:102";
        String requester = "clan:103";
        List<String> onlineMembers = Arrays.asList(onlineMember, null, staleMember, onlineMember);

        List<String> recipients = ClanTreasuryService.snapshotRecipients(
                onlineMembers, requester, value -> value != null && value.startsWith("clan:"));
        assertEquals("one online member plus requester", 2, recipients.size());
        assertTrue("online clan member included", recipients.contains(onlineMember));
        assertTrue("requester fallback included", recipients.contains(requester));
        assertFalse("stale member from another clan excluded", recipients.contains(staleMember));

        List<String> requesterAlreadyOnline = ClanTreasuryService.snapshotRecipients(
                onlineMembers, onlineMember, value -> value != null && value.startsWith("clan:"));
        assertEquals("online requester not duplicated", 1, requesterAlreadyOnline.size());

        System.out.println("ClanTreasurySnapshotRecipientsTest: PASS");
    }

    private static void assertEquals(String label, int expected, int actual) {
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
