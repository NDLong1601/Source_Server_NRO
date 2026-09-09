package nro.models.database;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicBoolean;
import nro.models.data.LocalManager;

/** Additive Gate 5 schema migration. No existing player column is rewritten. */
public final class PlayerPersistenceSchema {

    public static final int CURRENT_SCHEMA_VERSION = 1;
    private static final AtomicBoolean READY = new AtomicBoolean();
    private static final Object LOCK = new Object();

    private PlayerPersistenceSchema() {
    }

    public static void ensureReady() throws SQLException {
        if (READY.get()) {
            return;
        }
        synchronized (LOCK) {
            if (READY.get()) {
                return;
            }
            try (Connection connection = LocalManager.getConnection()) {
                ensureColumn(connection, "player", "save_version",
                        "ALTER TABLE player ADD COLUMN save_version BIGINT NOT NULL DEFAULT 0");
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate("CREATE TABLE IF NOT EXISTS player_wallet ("
                            + "player_id BIGINT NOT NULL PRIMARY KEY, gold BIGINT NOT NULL, gem INT NOT NULL, "
                            + "ruby INT NOT NULL, coupon INT NOT NULL, save_version BIGINT NOT NULL, "
                            + "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP)"
                    );
                    statement.executeUpdate("CREATE TABLE IF NOT EXISTS player_save_dead_letter ("
                            + "player_id BIGINT NOT NULL PRIMARY KEY, player_name VARCHAR(255) NULL, "
                            + "attempts INT NOT NULL, last_error VARCHAR(512) NOT NULL, "
                            + "failed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)"
                    );
                }
            }
            READY.set(true);
        }
    }

    private static void ensureColumn(Connection connection, String table, String column,
            String alterSql) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet columns = metadata.getColumns(connection.getCatalog(), null, table, column)) {
            if (columns.next()) {
                return;
            }
        }
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(alterSql);
        }
    }
}
