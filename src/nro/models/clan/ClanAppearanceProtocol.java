package nro.models.clan;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/** Versioned phase-5D cosmetic payload carried by command 127/action 128. */
public final class ClanAppearanceProtocol {

    public static final byte ACTION = (byte) 128;
    public static final int ACTION_UNSIGNED = 128;
    public static final int VERSION = 1;

    private ClanAppearanceProtocol() {
    }

    public static void write(DataOutput writer, int clanId, boolean enabled,
            ClanAppearancePolicy.Appearance appearance) throws IOException {
        writer.writeByte(ACTION);
        writer.writeByte(VERSION);
        writeDetails(writer, clanId, enabled, appearance);
    }

    public static void writeDetails(DataOutput writer, int clanId, boolean enabled,
            ClanAppearancePolicy.Appearance appearance) throws IOException {
        if (appearance == null) {
            throw new IllegalArgumentException("Clan appearance is required");
        }
        ClanAppearanceConfig.Tier tier = appearance.tier();
        ClanAppearanceConfig.Tier next = appearance.nextTier();
        writer.writeBoolean(enabled);
        writer.writeByte(appearance.appearanceVersion());
        writer.writeInt(clanId);
        writer.writeByte(tier.id());
        writer.writeByte(appearance.tierCount());
        writer.writeUTF(tier.name());
        writer.writeUTF(tier.title());
        writer.writeUTF(appearance.resourceName());
        writer.writeInt(tier.accentRgb());
        writer.writeByte(tier.auraStyle());
        writer.writeInt(appearance.clanLevel());
        writer.writeInt(appearance.treeLevel());
        writer.writeLong(appearance.clanValue());
        writer.writeLong(appearance.clanValueVersion());
        writer.writeLong(appearance.visualRevision());
        writer.writeByte(next == null ? -1 : next.id());
        writer.writeUTF(next == null ? "" : next.name());
        writer.writeInt(next == null ? 0 : next.minimumClanLevel());
        writer.writeInt(next == null ? 0 : next.minimumTreeLevel());
        writer.writeLong(next == null ? 0L : next.minimumClanValue());
        writer.writeInt(appearance.remainingClanLevels());
        writer.writeInt(appearance.remainingTreeLevels());
        writer.writeLong(appearance.remainingClanValue());
    }

    public static Snapshot read(DataInput reader) throws IOException {
        int action = reader.readUnsignedByte();
        int version = reader.readUnsignedByte();
        return readDetails(reader, action, version);
    }

    public static Snapshot readDetails(DataInput reader, int action, int version) throws IOException {
        if (version < 1) {
            throw new IOException("Clan appearance version không được hỗ trợ: " + version);
        }
        boolean enabled = reader.readBoolean();
        int appearanceVersion = reader.readUnsignedByte();
        int clanId = reader.readInt();
        int tierId = reader.readUnsignedByte();
        int tierCount = reader.readUnsignedByte();
        String tierName = reader.readUTF();
        String title = reader.readUTF();
        String resourceName = reader.readUTF();
        int accentRgb = reader.readInt();
        int auraStyle = reader.readUnsignedByte();
        int clanLevel = reader.readInt();
        int treeLevel = reader.readInt();
        long clanValue = Math.max(0L, reader.readLong());
        long clanValueVersion = Math.max(0L, reader.readLong());
        long visualRevision = Math.max(0L, reader.readLong());
        int nextTierId = reader.readByte();
        String nextTierName = reader.readUTF();
        int nextClanLevel = reader.readInt();
        int nextTreeLevel = reader.readInt();
        long nextClanValue = Math.max(0L, reader.readLong());
        int remainingClanLevels = Math.max(0, reader.readInt());
        int remainingTreeLevels = Math.max(0, reader.readInt());
        long remainingClanValue = Math.max(0L, reader.readLong());
        return new Snapshot(action, version, enabled, appearanceVersion, clanId, tierId,
                tierCount, tierName, title, resourceName, accentRgb, auraStyle, clanLevel,
                treeLevel, clanValue, clanValueVersion, visualRevision, nextTierId,
                nextTierName, nextClanLevel, nextTreeLevel, nextClanValue,
                remainingClanLevels, remainingTreeLevels, remainingClanValue);
    }

    public record Snapshot(int action, int version, boolean enabled, int appearanceVersion,
            int clanId, int tierId, int tierCount, String tierName, String title,
            String resourceName, int accentRgb, int auraStyle, int clanLevel, int treeLevel,
            long clanValue, long clanValueVersion, long visualRevision, int nextTierId,
            String nextTierName, int nextClanLevel, int nextTreeLevel, long nextClanValue,
            int remainingClanLevels, int remainingTreeLevels, long remainingClanValue) {
    }
}
