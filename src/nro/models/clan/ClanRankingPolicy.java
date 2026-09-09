package nro.models.clan;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Pure ordering and pagination contract for the phase-5C clan leaderboard. */
public final class ClanRankingPolicy {

    private static final Comparator<Candidate> ORDER = Comparator
            .comparingLong(Candidate::clanValue).reversed()
            .thenComparing(Comparator.comparingInt(Candidate::clanLevel).reversed())
            .thenComparing(Comparator.comparingInt(Candidate::treeLevel).reversed())
            .thenComparingLong(Candidate::createdAt)
            .thenComparingInt(Candidate::clanId);

    private ClanRankingPolicy() {
    }

    public static List<Candidate> sorted(List<Candidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        ArrayList<Candidate> result = new ArrayList<>(candidates.size());
        for (Candidate candidate : candidates) {
            if (candidate != null) {
                result.add(candidate);
            }
        }
        result.sort(ORDER);
        return List.copyOf(result);
    }

    public static Page page(List<Candidate> sorted, int requesterClanId, int rawPage,
            int rawPageSize, ClanRankingConfig config) {
        ClanRankingConfig safeConfig = config == null
                ? ClanRankingConfig.from(null) : config;
        List<Candidate> safeEntries = sorted == null ? List.of() : sorted;
        int pageNumber = safeConfig.normalizePage(rawPage);
        int pageSize = safeConfig.normalizePageSize(rawPageSize);
        int totalEntries = safeEntries.size();
        int totalPages = totalEntries == 0 ? 0 : (totalEntries + pageSize - 1) / pageSize;
        int requesterRank = 0;
        if (requesterClanId >= 0) {
            for (int index = 0; index < totalEntries; index++) {
                if (safeEntries.get(index).clanId() == requesterClanId) {
                    requesterRank = index + 1;
                    break;
                }
            }
        }
        long offset = (long) pageNumber * pageSize;
        List<Candidate> entries;
        if (offset >= totalEntries) {
            entries = List.of();
        } else {
            int from = (int) offset;
            int to = Math.min(totalEntries, from + pageSize);
            entries = List.copyOf(safeEntries.subList(from, to));
        }
        return new Page(pageNumber, pageSize, totalEntries, totalPages, requesterRank, entries);
    }

    public record Candidate(int clanId, String name, String shortName, int emblemId,
            int clanLevel, int treeLevel, int currentMembers, int maxMembers,
            long clanValue, long clanValueVersion, long createdAt, int leaderId,
            int leaderHead, int leaderBody, int leaderLeg) {

        public Candidate {
            name = safeText(name, 255);
            shortName = safeText(shortName, 16);
            clanLevel = Math.max(0, clanLevel);
            treeLevel = Math.max(0, treeLevel);
            currentMembers = Math.max(0, currentMembers);
            maxMembers = Math.max(0, maxMembers);
            clanValue = Math.max(0L, clanValue);
            clanValueVersion = Math.max(0L, clanValueVersion);
            createdAt = Math.max(0L, createdAt);
        }
    }

    public record Page(int pageNumber, int pageSize, int totalEntries, int totalPages,
            int requesterRank, List<Candidate> entries) {

        public Page {
            entries = entries == null ? List.of() : List.copyOf(entries);
        }
    }

    private static String safeText(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String cleaned = value.replace('\u0000', ' ').trim();
        return cleaned.length() <= maxLength ? cleaned : cleaned.substring(0, maxLength);
    }
}
