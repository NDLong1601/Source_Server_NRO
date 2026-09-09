package nro.models.clan;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Versioned detail payload carried by command 127/action 127. */
public final class ClanRankingProtocol {

    public static final int ACTION = 127;
    public static final int VERSION = 1;

    private ClanRankingProtocol() {
    }

    public static void write(DataOutput writer, ClanRankingPolicy.Page page,
            long boardVersion, long generatedAt) throws IOException {
        if (page == null || page.entries().size() > 50) {
            throw new IllegalArgumentException("Clan ranking page exceeds protocol row limit");
        }
        writer.writeByte(ACTION);
        writer.writeByte(VERSION);
        writer.writeShort(page.pageNumber());
        writer.writeByte(page.pageSize());
        writer.writeInt(page.totalEntries());
        writer.writeInt(page.totalPages());
        writer.writeInt(page.requesterRank());
        writer.writeLong(boardVersion);
        writer.writeLong(generatedAt);
        writer.writeByte(page.entries().size());
        int firstRank = page.pageNumber() * page.pageSize() + 1;
        for (int index = 0; index < page.entries().size(); index++) {
            ClanRankingPolicy.Candidate candidate = page.entries().get(index);
            writer.writeInt(firstRank + index);
            writer.writeInt(candidate.clanId());
            writer.writeInt(candidate.emblemId());
            writer.writeInt(candidate.clanLevel());
            writer.writeInt(candidate.treeLevel());
            writer.writeShort(candidate.currentMembers());
            writer.writeShort(candidate.maxMembers());
            writer.writeLong(candidate.clanValue());
            writer.writeLong(candidate.clanValueVersion());
            writer.writeInt(candidate.leaderId());
            writer.writeShort(candidate.leaderHead());
            writer.writeShort(candidate.leaderBody());
            writer.writeShort(candidate.leaderLeg());
            writer.writeUTF(candidate.name());
            writer.writeUTF(candidate.shortName());
        }
    }

    public static Snapshot read(DataInput reader) throws IOException {
        int action = reader.readUnsignedByte();
        int version = reader.readUnsignedByte();
        int pageNumber = reader.readUnsignedShort();
        int pageSize = reader.readUnsignedByte();
        int totalEntries = reader.readInt();
        int totalPages = reader.readInt();
        int requesterRank = reader.readInt();
        long boardVersion = reader.readLong();
        long generatedAt = reader.readLong();
        int rowCount = reader.readUnsignedByte();
        ArrayList<Entry> entries = new ArrayList<>(rowCount);
        for (int index = 0; index < rowCount; index++) {
            entries.add(new Entry(reader.readInt(), reader.readInt(), reader.readInt(),
                    reader.readInt(), reader.readInt(), reader.readUnsignedShort(),
                    reader.readUnsignedShort(), reader.readLong(), reader.readLong(),
                    reader.readInt(), reader.readShort(), reader.readShort(),
                    reader.readShort(), reader.readUTF(), reader.readUTF()));
        }
        return new Snapshot(action, version, pageNumber, pageSize, totalEntries, totalPages,
                requesterRank, boardVersion, generatedAt, List.copyOf(entries));
    }

    public record Entry(int rank, int clanId, int emblemId, int clanLevel, int treeLevel,
            int currentMembers, int maxMembers, long clanValue, long clanValueVersion,
            int leaderId, int leaderHead, int leaderBody, int leaderLeg,
            String name, String shortName) {
    }

    public record Snapshot(int action, int version, int pageNumber, int pageSize,
            int totalEntries, int totalPages, int requesterRank, long boardVersion,
            long generatedAt, List<Entry> entries) {
    }
}
