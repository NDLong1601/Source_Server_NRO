package nro.models.clan;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Optional extension appended to the legacy clan profile packet (-53).
 *
 * <p>The original packet encodes clan level as a signed byte and maximum
 * members as an unsigned byte. This block keeps the legacy prefix intact for
 * old clients, while updated clients can read the exact int values.</p>
 */
public final class ClanProfileV2 {

    public static final int MAGIC = 0x434C5632; // "CLV2"
    public static final byte VERSION = 1;

    private ClanProfileV2() {
    }

    public static void write(DataOutputStream writer, int clanId, int level,
            int maxMember, int currentMember) throws IOException {
        writer.writeInt(MAGIC);
        writer.writeByte(VERSION);
        writer.writeInt(clanId);
        writer.writeInt(level);
        writer.writeInt(maxMember);
        writer.writeInt(currentMember);
    }

    public static Profile read(DataInputStream reader) throws IOException {
        int magic = reader.readInt();
        if (magic != MAGIC) {
            throw new IOException("Clan profile V2 magic không hợp lệ");
        }

        int version = reader.readUnsignedByte();
        if (version < VERSION) {
            throw new IOException("Clan profile V2 version không được hỗ trợ: " + version);
        }

        return new Profile(version, reader.readInt(), reader.readInt(),
                reader.readInt(), reader.readInt());
    }

    public static final class Profile {

        public final int version;
        public final int clanId;
        public final int level;
        public final int maxMember;
        public final int currentMember;

        private Profile(int version, int clanId, int level, int maxMember, int currentMember) {
            this.version = version;
            this.clanId = clanId;
            this.level = level;
            this.maxMember = maxMember;
            this.currentMember = currentMember;
        }
    }
}
