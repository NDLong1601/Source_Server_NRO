package nro.models.clan;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Optional extension appended to the legacy clan profile packet (-53).
 *
 * <p>The original packet encodes clan level as a signed byte and maximum
 * members as an unsigned byte. This block keeps the legacy prefix intact for
 * old clients, while updated clients can read the exact int values.</p>
 */
public final class ClanProfileV2 {

    public static final int MAGIC = 0x434C5632; // "CLV2"
    public static final byte VERSION = 5;
    public static final int POTENTIAL_BRANCH_COUNT = 6;

    private ClanProfileV2() {
    }

    public static void write(DataOutputStream writer, int clanId, int level,
            int maxMember, int currentMember, long clanGold, long clanGem,
            long treasuryVersion, long memberContribution,
            List<ClanTreasuryService.LedgerEntry> ledgerEntries,
            long clanExp, long expRequired, int capsuleRequired,
            long goldRequired, long gemRequired, int potentialTotal,
            int potentialUnspent, long progressionVersion,
            int[] potentialRanks, List<ClanBuffService.BuffView> buffStates) throws IOException {
        writer.writeInt(MAGIC);
        writer.writeByte(VERSION);
        writer.writeInt(clanId);
        writer.writeInt(level);
        writer.writeInt(maxMember);
        writer.writeInt(currentMember);
        writer.writeLong(clanGold);
        writer.writeLong(clanGem);
        writer.writeLong(treasuryVersion);
        writer.writeLong(memberContribution);
        int ledgerCount = ledgerEntries == null ? 0 : Math.min(50, ledgerEntries.size());
        writer.writeByte(ledgerCount);
        for (int i = 0; i < ledgerCount; i++) {
            ClanTreasuryService.LedgerEntry entry = ledgerEntries.get(i);
            writer.writeLong(entry.id());
            writer.writeLong(entry.actorId());
            writer.writeUTF(entry.actorName() == null ? "" : entry.actorName());
            writer.writeUTF(entry.actionType() == null ? "" : entry.actionType());
            writer.writeByte(entry.currencyType());
            writer.writeLong(entry.amount());
            writer.writeLong(entry.balanceAfter());
            writer.writeLong(entry.createdAtSeconds());
        }
        writer.writeLong(clanExp);
        writer.writeLong(expRequired);
        writer.writeInt(capsuleRequired);
        writer.writeLong(goldRequired);
        writer.writeLong(gemRequired);
        writer.writeInt(potentialTotal);
        writer.writeInt(potentialUnspent);
        writer.writeLong(progressionVersion);
        for (int i = 0; i < POTENTIAL_BRANCH_COUNT; i++) {
            int rank = potentialRanks != null && i < potentialRanks.length
                    ? potentialRanks[i] : 0;
            writer.writeByte(Math.max(0, Math.min(255, rank)));
        }
        long snapshotAt = System.currentTimeMillis();
        int buffCount = buffStates == null ? 0 : Math.min(ClanBuffService.BuffType.values().length, buffStates.size());
        writer.writeByte(buffCount);
        for (int i = 0; i < buffCount; i++) {
            ClanBuffService.BuffView buff = buffStates.get(i);
            writer.writeByte(buff.typeId());
            writer.writeShort(buff.percent());
            writer.writeLong(Math.max(0L, buff.expiresAt() - snapshotAt));
        }
    }

    public static Profile read(DataInputStream reader) throws IOException {
        int magic = reader.readInt();
        if (magic != MAGIC) {
            throw new IOException("Clan profile V2 magic không hợp lệ");
        }

        int version = reader.readUnsignedByte();
        if (version < 1) {
            throw new IOException("Clan profile V2 version không được hỗ trợ: " + version);
        }

        int clanId = reader.readInt();
        int level = reader.readInt();
        int maxMember = reader.readInt();
        int currentMember = reader.readInt();
        long clanGold = version >= 2 ? reader.readLong() : 0L;
        long clanGem = version >= 2 ? reader.readLong() : 0L;
        long treasuryVersion = version >= 2 ? reader.readLong() : 0L;
        long memberContribution = version >= 2 ? reader.readLong() : 0L;
        List<ClanTreasuryService.LedgerEntry> ledgerEntries = new ArrayList<>();
        if (version >= 3) {
            int count = reader.readUnsignedByte();
            for (int i = 0; i < count; i++) {
                ledgerEntries.add(new ClanTreasuryService.LedgerEntry(
                        reader.readLong(), reader.readLong(), reader.readUTF(), reader.readUTF(),
                        reader.readByte(), reader.readLong(), reader.readLong(), reader.readLong()));
            }
        }
        long clanExp = 0L;
        long expRequired = 0L;
        int capsuleRequired = 0;
        long goldRequired = 0L;
        long gemRequired = 0L;
        int potentialTotal = 0;
        int potentialUnspent = 0;
        long progressionVersion = 0L;
        int[] potentialRanks = new int[POTENTIAL_BRANCH_COUNT];
        int[] buffPercents = new int[ClanBuffService.BuffType.values().length];
        long[] buffRemainingMillis = new long[ClanBuffService.BuffType.values().length];
        if (version >= 4) {
            clanExp = reader.readLong();
            expRequired = reader.readLong();
            capsuleRequired = reader.readInt();
            goldRequired = reader.readLong();
            gemRequired = reader.readLong();
            potentialTotal = reader.readInt();
            potentialUnspent = reader.readInt();
            progressionVersion = reader.readLong();
            for (int i = 0; i < POTENTIAL_BRANCH_COUNT; i++) {
                potentialRanks[i] = reader.readUnsignedByte();
            }
        }
        if (version >= 5) {
            int count = reader.readUnsignedByte();
            for (int i = 0; i < count; i++) {
                int type = reader.readUnsignedByte();
                int percent = reader.readShort();
                long remainingMillis = reader.readLong();
                if (type >= 0 && type < buffPercents.length) {
                    buffPercents[type] = percent;
                    buffRemainingMillis[type] = Math.max(0L, remainingMillis);
                }
            }
        }
        return new Profile(version, clanId, level, maxMember, currentMember,
                clanGold, clanGem, treasuryVersion, memberContribution, ledgerEntries,
                clanExp, expRequired, capsuleRequired, goldRequired, gemRequired,
                potentialTotal, potentialUnspent, progressionVersion, potentialRanks,
                buffPercents, buffRemainingMillis);
    }

    public static final class Profile {

        public final int version;
        public final int clanId;
        public final int level;
        public final int maxMember;
        public final int currentMember;
        public final long clanGold;
        public final long clanGem;
        public final long treasuryVersion;
        public final long memberContribution;
        public final List<ClanTreasuryService.LedgerEntry> ledgerEntries;
        public final long clanExp;
        public final long expRequired;
        public final int capsuleRequired;
        public final long goldRequired;
        public final long gemRequired;
        public final int potentialTotal;
        public final int potentialUnspent;
        public final long progressionVersion;
        public final int[] potentialRanks;
        public final int[] buffPercents;
        public final long[] buffRemainingMillis;

        private Profile(int version, int clanId, int level, int maxMember, int currentMember,
                long clanGold, long clanGem, long treasuryVersion, long memberContribution,
                List<ClanTreasuryService.LedgerEntry> ledgerEntries,
                long clanExp, long expRequired, int capsuleRequired,
                long goldRequired, long gemRequired, int potentialTotal,
                int potentialUnspent, long progressionVersion, int[] potentialRanks,
                int[] buffPercents, long[] buffRemainingMillis) {
            this.version = version;
            this.clanId = clanId;
            this.level = level;
            this.maxMember = maxMember;
            this.currentMember = currentMember;
            this.clanGold = clanGold;
            this.clanGem = clanGem;
            this.treasuryVersion = treasuryVersion;
            this.memberContribution = memberContribution;
            this.ledgerEntries = List.copyOf(ledgerEntries);
            this.clanExp = clanExp;
            this.expRequired = expRequired;
            this.capsuleRequired = capsuleRequired;
            this.goldRequired = goldRequired;
            this.gemRequired = gemRequired;
            this.potentialTotal = potentialTotal;
            this.potentialUnspent = potentialUnspent;
            this.progressionVersion = progressionVersion;
            this.potentialRanks = potentialRanks.clone();
            this.buffPercents = buffPercents.clone();
            this.buffRemainingMillis = buffRemainingMillis.clone();
        }
    }
}
