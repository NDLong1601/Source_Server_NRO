package nro.models.database;

import java.sql.Connection;
import java.util.function.Consumer;
import nro.models.player.Player;
import nro.models.player.PlayerPersistenceState;
import nro.models.player.PlayerSnapshot;
import nro.models.utils.Logger;
import nro.models.utils.TimeUtil;

/**
 * Compatibility facade. Snapshot mapping and JDBC persistence are deliberately
 * separate so this DAO never walks mutable Player components.
 */
public final class PlayerDAO {

    public enum SaveResult {
        SAVED,
        SKIPPED,
        IN_FLIGHT,
        OPTIMISTIC_CONFLICT,
        FAILED
    }

    private static final PlayerRepository REPOSITORY = new PlayerRepository();

    private PlayerDAO() {
    }

    public static SaveResult savePlayer(Player source) {
        if (source == null || source.isPersistenceQuarantined()) {
            return SaveResult.SKIPPED;
        }
        PlayerPersistenceState persistence = source.getPersistenceState();
        if (!persistence.tryBeginSave()) {
            if (!source.isRemovingOrDisposed() || !persistence.awaitSaveCompletion(5_000L)
                    || !persistence.tryBeginSave()) {
                return SaveResult.IN_FLIGHT;
            }
        }
        try {
            PlayerSnapshot snapshot = PlayerSnapshotMapper.capture(source);
            if (snapshot == null) {
                persistence.finishSave();
                return SaveResult.SKIPPED;
            }
            return persistCaptured(source, snapshot, REPOSITORY);
        } catch (Exception error) {
            persistence.finishSave();
            Logger.logException(PlayerDAO.class, error,
                    "Lỗi snapshot player " + (source.name == null ? source.id : source.name));
            return SaveResult.FAILED;
        }
    }

    public static boolean captureOnOwnerThread(Player source, Consumer<PlayerSnapshot> consumer) {
        if (source == null || consumer == null || source.isPersistenceQuarantined()
                || source.isRemovingOrDisposed() || !source.getPersistenceState().isDirty()) {
            return false;
        }
        return source.getMailbox().submit(() -> {
            PlayerPersistenceState persistence = source.getPersistenceState();
            if (!persistence.tryBeginSave()) {
                consumer.accept(null);
                return;
            }
            try {
                PlayerSnapshot snapshot = PlayerSnapshotMapper.capture(source);
                if (snapshot == null) {
                    persistence.finishSave();
                }
                consumer.accept(snapshot);
            } catch (RuntimeException error) {
                persistence.finishSave();
                throw error;
            }
        });
    }

    public static SaveResult persistCaptured(Player source, PlayerSnapshot snapshot,
            PlayerRepository repository) {
        if (source == null) {
            return SaveResult.FAILED;
        }
        PlayerPersistenceState persistence = source.getPersistenceState();
        long startedAt = System.currentTimeMillis();
        boolean databaseCommitted = false;
        try {
            if (snapshot == null || source.isPersistenceQuarantined()) {
                return SaveResult.SKIPPED;
            }
            if (repository == null) {
                return SaveResult.FAILED;
            }
            if (snapshot.playerId() != source.id
                    || snapshot.expectedSaveVersion() != persistence.saveVersion()) {
                source.quarantinePersistence();
                Logger.error("[Gate5] Snapshot identity/revision mismatch; persistence quarantined for playerId="
                        + source.id);
                return SaveResult.OPTIMISTIC_CONFLICT;
            }
            PlayerRepository.SaveStatus status = repository.save(snapshot);
            if (status == PlayerRepository.SaveStatus.OPTIMISTIC_CONFLICT) {
                source.quarantinePersistence();
                Logger.error("[Gate5] Optimistic player save conflict; persistence quarantined for playerId="
                        + snapshot.playerId());
                return SaveResult.OPTIMISTIC_CONFLICT;
            }
            databaseCommitted = true;
            persistence.acknowledge(snapshot.token(), snapshot.committedSaveVersion());
            String message = TimeUtil.getCurrHour() + "h" + TimeUtil.getCurrMin() + "m: Player "
                    + snapshot.playerName() + " saved at revision " + snapshot.committedSaveVersion()
                    + " in " + (System.currentTimeMillis() - startedAt) + "ms\n";
            Logger.success(Logger.PURPLE + message);
            if (snapshot.offline()) {
                source.dispose();
            }
            return SaveResult.SAVED;
        } catch (Exception error) {
            if (databaseCommitted) {
                source.quarantinePersistence();
                Logger.error("[Gate5] Save committed but local revision acknowledgement failed; "
                        + "persistence quarantined for playerId=" + source.id);
            }
            Logger.logException(PlayerDAO.class, error,
                    "Lỗi save player " + (source.name == null ? source.id : source.name));
            return SaveResult.FAILED;
        } finally {
            persistence.finishSave();
        }
    }

    public static void updatePlayer(Player player) {
        savePlayer(player);
    }

    public static void updateEventRankingPoints(Player player) {
        PlayerSnapshotMapper.updateEventRankingPoints(player);
    }

    public static boolean createNewPlayer(int userId, String name, byte gender, int hair) {
        return PlayerSnapshotMapper.createNewPlayer(userId, name, gender, hair);
    }

    public static boolean checkLogout(Connection connection, Player player) {
        return PlayerSnapshotMapper.checkLogout(connection, player);
    }

    public static void LogAddPoint(String name, int id, int point, String type) {
        PlayerSnapshotMapper.LogAddPoint(name, id, point, type);
    }
}
