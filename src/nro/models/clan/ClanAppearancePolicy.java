package nro.models.clan;

/** Pure automatic unlock policy. Appearance never modifies combat statistics. */
public final class ClanAppearancePolicy {

    private static final long FNV_OFFSET = 0xCBF29CE484222325L;
    private static final long FNV_PRIME = 0x100000001B3L;

    private ClanAppearancePolicy() {
    }

    public static Appearance resolve(ClanAppearanceConfig config, int rawClanLevel,
            int rawTreeLevel, long rawClanValue, long rawClanValueVersion) {
        ClanAppearanceConfig safeConfig = config == null
                ? ClanAppearanceConfig.from(null) : config;
        int clanLevel = Math.max(0, rawClanLevel);
        int treeLevel = Math.max(0, rawTreeLevel);
        long clanValue = Math.max(0L, rawClanValue);
        long clanValueVersion = Math.max(0L, rawClanValueVersion);
        ClanAppearanceConfig.Tier current = safeConfig.tier(0);
        for (int index = 1; index < safeConfig.tierCount(); index++) {
            ClanAppearanceConfig.Tier candidate = safeConfig.tier(index);
            if (clanValue >= candidate.minimumClanValue()
                    && clanLevel >= candidate.minimumClanLevel()
                    && treeLevel >= candidate.minimumTreeLevel()) {
                current = candidate;
            }
        }
        ClanAppearanceConfig.Tier next = current.id() + 1 < safeConfig.tierCount()
                ? safeConfig.tier(current.id() + 1) : null;
        int remainingClanLevels = next == null ? 0
                : Math.max(0, next.minimumClanLevel() - clanLevel);
        int remainingTreeLevels = next == null ? 0
                : Math.max(0, next.minimumTreeLevel() - treeLevel);
        long remainingClanValue = next == null ? 0L
                : Math.max(0L, next.minimumClanValue() - clanValue);
        long visualRevision = visualRevision(safeConfig.appearanceVersion(), current.id(),
                treeLevel, clanValueVersion);
        return new Appearance(safeConfig.appearanceVersion(), safeConfig.tierCount(), current,
                next, safeConfig.resourceName(treeLevel), clanLevel, treeLevel, clanValue,
                clanValueVersion, remainingClanLevels, remainingTreeLevels,
                remainingClanValue, visualRevision);
    }

    private static long visualRevision(int version, int tierId, int treeLevel, long valueVersion) {
        long hash = FNV_OFFSET;
        hash = (hash ^ version) * FNV_PRIME;
        hash = (hash ^ tierId) * FNV_PRIME;
        hash = (hash ^ treeLevel) * FNV_PRIME;
        hash = (hash ^ valueVersion) * FNV_PRIME;
        hash &= Long.MAX_VALUE;
        return hash == 0L ? 1L : hash;
    }

    public record Appearance(int appearanceVersion, int tierCount,
            ClanAppearanceConfig.Tier tier, ClanAppearanceConfig.Tier nextTier,
            String resourceName, int clanLevel, int treeLevel, long clanValue,
            long clanValueVersion, int remainingClanLevels, int remainingTreeLevels,
            long remainingClanValue, long visualRevision) {
    }
}
