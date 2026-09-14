package nro.models.social;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import nro.models.player.Player;
import nro.models.utils.Util;

/** Builds safe profile DTOs from either an active Player or a narrow offline projection. */
public final class SocialProfileService {

    public static final String ACTIVITY_ONLINE = "Đang online";
    public static final String ACTIVITY_FREQUENT = "Thường xuyên";
    public static final String ACTIVITY_RECENT = "Gần đây";
    public static final String ACTIVITY_INACTIVE = "Ít hoạt động";

    public record Profile(long playerId, String name, short head, String clanName, String activityLabel,
            long rawPower, String formattedPower, boolean online) {
        public Profile {
            if (playerId <= 0L || name == null || name.isBlank() || clanName == null
                    || activityLabel == null || rawPower < 0L || formattedPower == null) {
                throw new IllegalArgumentException("Social profile is invalid");
            }
        }
    }

    private final SocialProfileRepository repository;

    public SocialProfileService(SocialProfileRepository repository) {
        if (repository == null) {
            throw new IllegalArgumentException("Social profile repository is required");
        }
        this.repository = repository;
    }

    /** Never loads a Player for an offline target. */
    public Profile loadOffline(long playerId, Instant now) throws SQLException {
        if (playerId <= 0L || now == null) {
            throw new IllegalArgumentException("Player id and current time are required for a social profile");
        }
        SocialProfileRepository.StoredProfile stored = repository.findByPlayerId(playerId);
        if (stored == null) {
            return null;
        }
        return new Profile(stored.playerId(), stored.name(), stored.head(), stored.clanName(),
                activityLabel(stored.lastActivityAt(), now), stored.rawPower(),
                Util.numberToMoney(stored.rawPower()), false);
    }

    public Profile fromOnline(Player player) {
        if (player == null || player.id <= 0L || player.name == null || player.name.isBlank()) {
            throw new IllegalArgumentException("Online player is invalid for a social profile");
        }
        long rawPower = player.nPoint == null ? 0L : Math.max(0L, player.nPoint.power);
        String clanName = player.clan == null || player.clan.name == null ? "" : player.clan.name;
        return new Profile(player.id, player.name, player.getHead(), clanName, ACTIVITY_ONLINE, rawPower,
                Util.numberToMoney(rawPower), true);
    }

    public static String activityLabel(Instant lastActivityAt, Instant now) {
        if (now == null) {
            throw new IllegalArgumentException("Current time is required for social activity");
        }
        if (lastActivityAt == null) {
            return ACTIVITY_INACTIVE;
        }
        long days;
        try {
            days = Math.max(0L, Duration.between(lastActivityAt, now).toDays());
        } catch (ArithmeticException overflow) {
            return ACTIVITY_INACTIVE;
        }
        if (days <= 7L) {
            return ACTIVITY_FREQUENT;
        }
        return days <= 30L ? ACTIVITY_RECENT : ACTIVITY_INACTIVE;
    }
}
