package nro.models.player;

/** Immutable RPO, staggering, and retry policy shared by runtime and tests. */
public record PlayerAutosavePolicy(long rpoMillis, long retryBaseMillis, int maxAttempts) {

    public PlayerAutosavePolicy {
        if (rpoMillis < 10_000L || retryBaseMillis < 100L || retryBaseMillis > rpoMillis
                || maxAttempts < 1 || maxAttempts > 20) {
            throw new IllegalArgumentException("Invalid player autosave policy");
        }
    }

    public long firstDueAt(long playerId, long now) {
        return now + Math.floorMod(playerId, rpoMillis + 1L);
    }

    public boolean isDue(long now, long dueAt) {
        return now >= dueAt;
    }

    public long retryDelayMillis(int attempt) {
        int exponent = Math.max(0, Math.min(20, attempt - 1));
        long multiplier = 1L << exponent;
        long delay;
        try {
            delay = Math.multiplyExact(retryBaseMillis, multiplier);
        } catch (ArithmeticException ignored) {
            delay = rpoMillis;
        }
        return Math.min(rpoMillis, delay);
    }
}
