package nro.models.server;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.EnumMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import nro.models.data.LocalManager;
import nro.models.matches.TOP;
import nro.models.utils.Util;

/** Single owner of leaderboard queries, snapshots, and invalidation state. */
public final class LeaderboardService {

    public enum Board {
        MACHINE("SELECT id, point_maydam, total_damage_maydam FROM player ORDER BY point_maydam DESC LIMIT 100"),
        EVENT("SELECT id, point_sukien FROM player ORDER BY point_sukien DESC LIMIT 100"),
        EVENT_ONE("SELECT id, point_sukien1 FROM player ORDER BY point_sukien1 DESC LIMIT 100"),
        EVENT_TWO("SELECT id, point_sukien2 FROM player ORDER BY point_sukien2 DESC LIMIT 100"),
        WHIS("SELECT id, thachdauwhis FROM player ORDER BY thachdauwhis DESC LIMIT 100");

        private final String query;

        Board(String query) { this.query = query; }
    }

    private final EnumMap<Board, AtomicLong> invalidations = new EnumMap<>(Board.class);
    private volatile Map<Board, List<TOP>> snapshots = emptySnapshots();

    public LeaderboardService() {
        for (Board board : Board.values()) invalidations.put(board, new AtomicLong());
    }

    public void loadAll(Connection connection) throws SQLException {
        EnumMap<Board, List<TOP>> candidate = new EnumMap<>(Board.class);
        for (Board board : Board.values()) candidate.put(board, query(board, connection));
        snapshots = Map.copyOf(candidate);
        clearDirty();
    }

    public void refreshDirty() throws SQLException {
        List<Board> requested = dirtyBoards();
        if (requested.isEmpty()) return;
        EnumMap<Board, Long> observedVersions = new EnumMap<>(Board.class);
        for (Board board : requested) observedVersions.put(board, invalidations.get(board).get());
        try (Connection connection = LocalManager.getConnection()) {
            EnumMap<Board, List<TOP>> candidate = new EnumMap<>(snapshots);
            for (Board board : requested) candidate.put(board, query(board, connection));
            snapshots = Map.copyOf(candidate);
        }
        for (Board board : requested) {
            invalidations.get(board).compareAndSet(observedVersions.get(board), 0);
        }
    }

    public List<TOP> get(Board board) { return snapshots.getOrDefault(board, List.of()); }

    public void markDirty(Board board) {
        if (board == null) throw new IllegalArgumentException("board must not be null");
        invalidations.get(board).incrementAndGet();
    }

    public void clearDirty(Board board) { invalidations.get(board).set(0); }
    public void clearDirty() { invalidations.values().forEach(value -> value.set(0)); }
    public boolean hasDirtyBoards() {
        return invalidations.values().stream().anyMatch(value -> value.get() != 0);
    }

    public List<Board> dirtyBoards() {
        List<Board> result = new ArrayList<>();
        for (Board board : Board.values()) {
            if (invalidations.get(board).get() != 0) result.add(board);
        }
        return List.copyOf(result);
    }

    private List<TOP> query(Board board, Connection connection) throws SQLException {
        java.util.ArrayList<TOP> values = new java.util.ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(board.query);
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                TOP top = TOP.builder().id_player(result.getInt("id")).build();
                switch (board) {
                    case EVENT -> setPoints(top, result.getInt("point_sukien"));
                    case EVENT_ONE -> setPoints(top, result.getInt("point_sukien1"));
                    case EVENT_TWO -> setPoints(top, result.getInt("point_sukien2"));
                    case WHIS -> {
                        int level = result.getInt("thachdauwhis");
                        top.setInfo1(level + " Level");
                        top.setInfo2(level + " Level");
                    }
                    case MACHINE -> {
                        int points = result.getInt("point_maydam");
                        top.setInfo1(points + " điểm");
                        top.setInfo2(Util.formatNumber(result.getLong("total_damage_maydam")) + " sát thương");
                    }
                }
                values.add(top);
            }
        }
        return List.copyOf(values);
    }

    private static void setPoints(TOP top, int points) {
        top.setInfo1(points + " điểm");
        top.setInfo2(points + " điểm");
    }

    private static Map<Board, List<TOP>> emptySnapshots() {
        EnumMap<Board, List<TOP>> empty = new EnumMap<>(Board.class);
        for (Board board : Board.values()) empty.put(board, List.of());
        return Map.copyOf(empty);
    }
}
