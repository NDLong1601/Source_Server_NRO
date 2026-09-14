package nro.models.social;

import java.sql.SQLException;
import java.time.Instant;

/**
 * Narrow offline profile projection. Implementations must not materialize a
 * Player, inventory, account credentials, or any chat history for this read.
 */
@FunctionalInterface
public interface SocialProfileRepository {

    record StoredProfile(long playerId, String name, short head, String clanName, long rawPower,
            Instant lastActivityAt) {
        public StoredProfile {
            if (playerId <= 0L || name == null || name.isBlank() || clanName == null || rawPower < 0L) {
                throw new IllegalArgumentException("Stored social profile is invalid");
            }
        }
    }

    StoredProfile findByPlayerId(long playerId) throws SQLException;
}
