package nro.models.player;

public record PlayerRankSnapshot(int rank, String name, String infoJson,
        long lastPkTime, long lastRewardTime, int ticket, int win, int lose,
        String historyJson) {
}
