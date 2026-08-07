package io.github.blackshadowhrd.gameloot.repository;

import io.github.blackshadowhrd.gameloot.database.DatabaseManager;
import io.github.blackshadowhrd.gameloot.model.LootPointType;
import io.github.blackshadowhrd.gameloot.repository.model.ClaimRecord;
import io.github.blackshadowhrd.gameloot.repository.model.LootPointDeletion;
import io.github.blackshadowhrd.gameloot.repository.model.LootPointRecord;
import org.bukkit.NamespacedKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepositoryIntegrationTest {

    @TempDir
    Path temporaryDirectory;

    private Path databasePath;
    private DatabaseManager databaseManager;
    private LootPointRepository lootPoints;
    private ClaimRepository claims;

    @BeforeEach
    void setUp() {
        databasePath = temporaryDirectory.resolve("gameloot.db");
        openDatabase();
    }

    @AfterEach
    void tearDown() {
        if (databaseManager != null) {
            databaseManager.shutdown();
        }
    }

    @Test
    void createsSchemaAndAppliesMigrationIdempotently() {
        assertEquals(2, schemaVersion());

        databaseManager.shutdown();
        openDatabase();

        assertEquals(2, schemaVersion());
    }

    @Test
    void migratesLegacyLootTableNamespace() {
        LootPointRecord legacy = record(NamespacedKey.fromString("poiloot:mining_camp/common"));
        lootPoints.insert(legacy).join();
        databaseManager.executeBlocking(connection -> {
            try (var statement = connection.prepareStatement("UPDATE schema_version SET version = 1")) {
                statement.executeUpdate();
            }
            return null;
        });

        databaseManager.shutdown();
        openDatabase();

        LootPointRecord migrated = lootPoints.findById(legacy.id()).join().orElseThrow();
        assertEquals(NamespacedKey.fromString("gameloot:mining_camp/common"), migrated.lootTable());
    }

    @Test
    void movesLegacyDatabaseFileWhenNewFileDoesNotExist() throws Exception {
        databaseManager.shutdown();
        Path legacyPath = temporaryDirectory.resolve("POILoot").resolve("poiloot.db");
        Files.createDirectories(legacyPath.getParent());
        Files.move(databasePath, legacyPath);

        DatabaseManager.migrateLegacyDatabase(
                legacyPath,
                databasePath,
                Logger.getLogger("GameLootTest")
        );

        assertTrue(Files.exists(databasePath));
        assertFalse(Files.exists(legacyPath));
    }

    @Test
    void insertsLooksUpAndDeletesLootPoint() {
        LootPointRecord record = record();

        assertTrue(lootPoints.insert(record).join());
        assertEquals(record, lootPoints.findById(record.id()).join().orElseThrow());
        assertEquals(record, lootPoints.delete(record.id()).join().orElseThrow().lootPoint());
        assertTrue(lootPoints.findById(record.id()).join().isEmpty());
    }

    @Test
    void rejectsDuplicateLootPointId() {
        LootPointRecord record = record();

        assertTrue(lootPoints.insert(record).join());
        assertFalse(lootPoints.insert(record).join());
        assertEquals(1, lootPoints.findAllBlocking().size());
    }

    @Test
    void insertsClaimAndPreventsDuplicateClaim() {
        LootPointRecord point = record();
        lootPoints.insert(point).join();
        ClaimRecord claim = new ClaimRecord(UUID.randomUUID(), point.id(), 1234L);

        assertTrue(claims.insert(claim).join());
        assertFalse(claims.insert(claim).join());
        assertEquals(java.util.List.of(claim), claims.findAllBlocking());
    }

    @Test
    void deletingLootPointCascadesClaims() {
        LootPointRecord point = record();
        lootPoints.insert(point).join();
        claims.insert(new ClaimRecord(UUID.randomUUID(), point.id(), 1234L)).join();

        lootPoints.delete(point.id()).join();

        assertTrue(claims.findAllBlocking().isEmpty());
    }

    @Test
    void restoresLootPointAndClaimsAfterCompensatedDeletion() {
        LootPointRecord point = record();
        ClaimRecord claim = new ClaimRecord(UUID.randomUUID(), point.id(), 1234L);
        lootPoints.insert(point).join();
        claims.insert(claim).join();

        LootPointDeletion deletion = lootPoints.delete(point.id()).join().orElseThrow();
        assertTrue(lootPoints.restore(deletion).join());

        assertEquals(point, lootPoints.findById(point.id()).join().orElseThrow());
        assertEquals(java.util.List.of(claim), claims.findAllBlocking());
    }

    private void openDatabase() {
        databaseManager = new DatabaseManager(databasePath, Logger.getLogger("GameLootTest"));
        databaseManager.initialize(databasePath);
        lootPoints = new LootPointRepository(databaseManager);
        claims = new ClaimRepository(databaseManager);
    }

    private int schemaVersion() {
        return databaseManager.executeBlocking(connection -> {
            try (var statement = connection.prepareStatement("SELECT version FROM schema_version");
                 var result = statement.executeQuery()) {
                result.next();
                return result.getInt("version");
            }
        });
    }

    private LootPointRecord record() {
        return record(NamespacedKey.minecraft("chests/simple_dungeon"));
    }

    private LootPointRecord record(NamespacedKey lootTable) {
        return new LootPointRecord(
                UUID.randomUUID(),
                UUID.randomUUID(),
                LootPointType.BLOCK_CONTAINER,
                10,
                64,
                -20,
                null,
                lootTable,
                1234L,
                UUID.randomUUID()
        );
    }
}
