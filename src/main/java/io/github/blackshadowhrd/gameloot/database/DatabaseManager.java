package io.github.blackshadowhrd.gameloot.database;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

public final class DatabaseManager {

    private static final int CURRENT_SCHEMA_VERSION = 3;
    private static final long SHUTDOWN_TIMEOUT_SECONDS = 10;

    private final String jdbcUrl;
    private final Logger logger;
    private final ExecutorService executor;
    private final AtomicBoolean acceptingWork = new AtomicBoolean();

    public DatabaseManager(Path databasePath, Logger logger) {
        this.jdbcUrl = "jdbc:sqlite:" + databasePath.toAbsolutePath();
        this.logger = Objects.requireNonNull(logger, "logger");
        executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "GameLoot database");
            thread.setDaemon(true);
            return thread;
        });
    }

    public static void migrateLegacyDatabase(Path legacyPath, Path databasePath, Logger logger) {
        if (Files.notExists(legacyPath) || Files.exists(databasePath)) {
            return;
        }

        try {
            Files.createDirectories(databasePath.toAbsolutePath().getParent());
            Files.move(legacyPath, databasePath, StandardCopyOption.ATOMIC_MOVE);
            logger.info("Migrated legacy POILoot database to " + databasePath);
        } catch (Exception atomicMoveFailure) {
            try {
                Files.move(legacyPath, databasePath);
                logger.info("Migrated legacy POILoot database to " + databasePath);
            } catch (Exception exception) {
                exception.addSuppressed(atomicMoveFailure);
                throw new DatabaseException(
                        "Failed to migrate legacy database from " + legacyPath + " to " + databasePath,
                        exception
                );
            }
        }
    }

    public void initialize(Path databasePath) {
        try {
            Files.createDirectories(databasePath.toAbsolutePath().getParent());
            Class.forName("org.sqlite.JDBC");
            try (Connection connection = openConnection()) {
                migrate(connection);
            }
            acceptingWork.set(true);
        } catch (Exception exception) {
            throw new DatabaseException("Failed to initialize SQLite database at " + databasePath, exception);
        }
    }

    public <T> CompletableFuture<T> submit(DatabaseOperation<T> operation) {
        if (!acceptingWork.get()) {
            return CompletableFuture.failedFuture(new RejectedExecutionException("Database is shutting down"));
        }

        try {
            return CompletableFuture.supplyAsync(() -> {
                try (Connection connection = openConnection()) {
                    return operation.execute(connection);
                } catch (SQLException exception) {
                    throw new DatabaseException("Database operation failed", exception);
                }
            }, executor);
        } catch (RejectedExecutionException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    public <T> T executeBlocking(DatabaseOperation<T> operation) {
        try (Connection connection = openConnection()) {
            return operation.execute(connection);
        } catch (SQLException exception) {
            throw new DatabaseException("Database operation failed", exception);
        }
    }

    public void shutdown() {
        acceptingWork.set(false);
        executor.shutdown();
        try {
            if (!executor.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                logger.warning("GameLoot database shutdown timed out; cancelling unfinished work");
                executor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            logger.warning("GameLoot database shutdown was interrupted; cancelling unfinished work");
            executor.shutdownNow();
        }
    }

    private Connection openConnection() throws SQLException {
        Connection connection = DriverManager.getConnection(jdbcUrl);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA busy_timeout = 5000");
        }
        return connection;
    }

    private void migrate(Connection connection) throws SQLException {
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS schema_version (version INTEGER NOT NULL)");
            int version;
            try (ResultSet result = statement.executeQuery("SELECT version FROM schema_version LIMIT 1")) {
                version = result.next() ? result.getInt("version") : 0;
            }
            if (version == 0) {
                statement.executeUpdate("DELETE FROM schema_version");
                statement.executeUpdate("INSERT INTO schema_version(version) VALUES (0)");
            }
            if (version < 1) {
                applyVersionOne(statement);
                statement.executeUpdate("UPDATE schema_version SET version = 1");
                version = 1;
            }
            if (version < 2) {
                applyVersionTwo(statement);
                statement.executeUpdate("UPDATE schema_version SET version = 2");
                version = 2;
            }
            if (version < 3) {
                applyVersionThree(statement);
                statement.executeUpdate("UPDATE schema_version SET version = 3");
                version = 3;
            }
            if (version != CURRENT_SCHEMA_VERSION) {
                throw new SQLException("Unsupported schema version " + version);
            }
            connection.commit();
        } catch (SQLException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    private void applyVersionOne(Statement statement) throws SQLException {
        statement.execute("""
                CREATE TABLE loot_points (
                    id TEXT PRIMARY KEY,
                    world_uuid TEXT NOT NULL,
                    target_type TEXT NOT NULL,
                    x INTEGER NOT NULL,
                    y INTEGER NOT NULL,
                    z INTEGER NOT NULL,
                    entity_uuid TEXT,
                    loot_table TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    created_by TEXT
                )
                """);
        statement.execute("""
                CREATE TABLE loot_claims (
                    player_uuid TEXT NOT NULL,
                    loot_point_id TEXT NOT NULL,
                    claimed_at INTEGER NOT NULL,
                    PRIMARY KEY (player_uuid, loot_point_id),
                    FOREIGN KEY (loot_point_id) REFERENCES loot_points(id) ON DELETE CASCADE
                )
                """);
    }

    private void applyVersionTwo(Statement statement) throws SQLException {
        statement.executeUpdate("""
                UPDATE loot_points
                SET loot_table = 'gameloot:' || substr(loot_table, length('poiloot:') + 1)
                WHERE loot_table LIKE 'poiloot:%'
                """);
    }

    private void applyVersionThree(Statement statement) throws SQLException {
        statement.execute("""
                CREATE TABLE loot_points_v3 (
                    id TEXT PRIMARY KEY,
                    world_uuid TEXT NOT NULL,
                    target_type TEXT NOT NULL,
                    x INTEGER NOT NULL,
                    y INTEGER NOT NULL,
                    z INTEGER NOT NULL,
                    entity_uuid TEXT,
                    loot_table TEXT,
                    created_at INTEGER NOT NULL,
                    created_by TEXT,
                    CHECK ((target_type = 'SHELF' AND loot_table IS NULL)
                        OR (target_type <> 'SHELF' AND loot_table IS NOT NULL))
                )
                """);
        statement.executeUpdate("INSERT INTO loot_points_v3 SELECT * FROM loot_points");
        statement.execute("CREATE TEMP TABLE loot_claims_v3 AS SELECT * FROM loot_claims");
        statement.execute("DROP TABLE loot_claims");
        statement.execute("DROP TABLE loot_points");
        statement.execute("ALTER TABLE loot_points_v3 RENAME TO loot_points");
        statement.execute("""
                CREATE TABLE loot_claims (
                    player_uuid TEXT NOT NULL,
                    loot_point_id TEXT NOT NULL,
                    claimed_at INTEGER NOT NULL,
                    PRIMARY KEY (player_uuid, loot_point_id),
                    FOREIGN KEY (loot_point_id) REFERENCES loot_points(id) ON DELETE CASCADE
                )
                """);
        statement.executeUpdate("INSERT INTO loot_claims SELECT * FROM loot_claims_v3");
        statement.execute("DROP TABLE loot_claims_v3");
        statement.execute("""
                CREATE TABLE IF NOT EXISTS shelf_loot_items (
                    loot_point_id TEXT NOT NULL,
                    slot INTEGER NOT NULL CHECK (slot BETWEEN 0 AND 2),
                    serialized_item BLOB NOT NULL,
                    PRIMARY KEY (loot_point_id, slot),
                    FOREIGN KEY (loot_point_id) REFERENCES loot_points(id) ON DELETE CASCADE
                )
                """);
    }

    @FunctionalInterface
    public interface DatabaseOperation<T> {
        T execute(Connection connection) throws SQLException;
    }
}
