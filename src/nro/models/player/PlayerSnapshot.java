package nro.models.player;

import java.util.Objects;
import java.util.Locale;

/** Immutable, DB-ready snapshot detached from the live player aggregate. */
public final class PlayerSnapshot {

    private final long playerId;
    private final String playerName;
    private final boolean offline;
    private final long expectedSaveVersion;
    private final int schemaVersion;
    private final String updateSql;
    private final Object[] parameters;
    private final PlayerPersistenceToken token;
    private final PlayerWalletSnapshot wallet;
    private final PlayerRankSnapshot superRank;

    public PlayerSnapshot(long playerId, String playerName, boolean offline,
            long expectedSaveVersion, int schemaVersion, String updateSql,
            Object[] parameters, PlayerPersistenceToken token,
            PlayerWalletSnapshot wallet, PlayerRankSnapshot superRank) {
        if (playerId <= 0L || expectedSaveVersion < 0L || schemaVersion < 1) {
            throw new IllegalArgumentException("Invalid player snapshot identity/version");
        }
        this.playerId = playerId;
        this.playerName = playerName;
        this.offline = offline;
        this.expectedSaveVersion = expectedSaveVersion;
        this.schemaVersion = schemaVersion;
        this.updateSql = Objects.requireNonNull(updateSql, "updateSql");
        this.parameters = Objects.requireNonNull(parameters, "parameters").clone();
        this.token = Objects.requireNonNull(token, "token");
        this.wallet = Objects.requireNonNull(wallet, "wallet");
        this.superRank = superRank;
        String normalizedSql = updateSql.toLowerCase(Locale.ROOT);
        if (!normalizedSql.contains("save_version")
                || !normalizedSql.matches("(?s).*where\\s+id\\s*=\\s*\\?\\s+and\\s+save_version\\s*=\\s*\\?.*")) {
            throw new IllegalArgumentException("Player snapshot SQL must use optimistic save_version locking");
        }
        if (updateSql.chars().filter(character -> character == '?').count() != this.parameters.length) {
            throw new IllegalArgumentException("Player snapshot SQL parameter count mismatch");
        }
        if (token.expectedSaveVersion() != expectedSaveVersion) {
            throw new IllegalArgumentException("Persistence token belongs to another save revision");
        }
    }

    public long playerId() { return playerId; }
    public String playerName() { return playerName; }
    public boolean offline() { return offline; }
    public long expectedSaveVersion() { return expectedSaveVersion; }
    public long committedSaveVersion() { return expectedSaveVersion + 1L; }
    public int schemaVersion() { return schemaVersion; }
    public String updateSql() { return updateSql; }
    public Object[] parameters() { return parameters.clone(); }
    public PlayerPersistenceToken token() { return token; }
    public PlayerWalletSnapshot wallet() { return wallet; }
    public PlayerRankSnapshot superRank() { return superRank; }
}
