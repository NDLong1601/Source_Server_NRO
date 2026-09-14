package nro.models.social;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

/** Read-only social directory boundary; it never exposes account or inventory data. */
public interface SocialDirectoryRepository {

    enum Relationship {
        FRIEND,
        PENDING,
        CAN_ADD
    }

    record PlayerSummary(long playerId, String name, short head) {
        public PlayerSummary {
            if (playerId <= 0L || name == null || name.isBlank()) {
                throw new IllegalArgumentException("Social player summary is invalid");
            }
        }
    }

    record SearchEntry(PlayerSummary player, Relationship relationship) {
        public SearchEntry {
            if (player == null || relationship == null) {
                throw new IllegalArgumentException("Social search entry is invalid");
            }
        }
    }

    record InboxEntry(long requestId, PlayerSummary sender, Instant expiresAt) {
        public InboxEntry {
            if (requestId <= 0L || sender == null || expiresAt == null) {
                throw new IllegalArgumentException("Social inbox entry is invalid");
            }
        }
    }

    List<SearchEntry> search(long viewerId, String normalizedQuery, int offset, int limit) throws SQLException;

    List<InboxEntry> inbox(long receiverId, int offset, int limit, Instant now) throws SQLException;

    List<PlayerSummary> friends(long playerId) throws SQLException;

    int pendingCount(long receiverId, Instant now) throws SQLException;
}
