import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import nro.models.clan.ClanProfileV2;

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
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream writer = new DataOutputStream(bytes)) {
            ClanProfileV2.write(writer, clanId, level, maxMember, currentMember);
        }

        try (DataInputStream reader = new DataInputStream(
                new ByteArrayInputStream(bytes.toByteArray()))) {
            ClanProfileV2.Profile profile = ClanProfileV2.read(reader);
            assertEquals("clanId", clanId, profile.clanId);
            assertEquals("level", level, profile.level);
            assertEquals("maxMember", maxMember, profile.maxMember);
            assertEquals("currentMember", currentMember, profile.currentMember);
        }
    }

    private static void assertEquals(String field, int expected, int actual) {
        if (expected != actual) {
            throw new AssertionError(field + ": expected=" + expected + ", actual=" + actual);
        }
    }
}
