package io.github.blackshadowhrd.gameloot.repository;

import io.github.blackshadowhrd.gameloot.database.DatabaseManager;
import io.github.blackshadowhrd.gameloot.model.LootPointType;
import io.github.blackshadowhrd.gameloot.repository.model.ClaimRecord;
import io.github.blackshadowhrd.gameloot.repository.model.LootPointDeletion;
import io.github.blackshadowhrd.gameloot.repository.model.LootPointRecord;
import io.github.blackshadowhrd.gameloot.repository.model.ShelfRewardItem;
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
    private ValidationRepository validation;

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
        assertEquals(3, schemaVersion());

        databaseManager.shutdown();
        openDatabase();

        assertEquals(3, schemaVersion());
    }

    @Test
    void migratesLegacyLootTableNamespace() {
        LootPointRecord legacy = record(NamespacedKey.fromString("poiloot:mining_camp/common"));
        lootPoints.insert(legacy).join();
        ClaimRecord existingClaim = new ClaimRecord(UUID.randomUUID(), legacy.id(), 999L);
        claims.insert(existingClaim).join();
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
        assertEquals(java.util.List.of(existingClaim), claims.findAllBlocking());
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
    void countsClaimsForLootPoint() {
        LootPointRecord point = record();
        lootPoints.insert(point).join();
        claims.insert(new ClaimRecord(UUID.randomUUID(), point.id(), 1000L)).join();
        claims.insert(new ClaimRecord(UUID.randomUUID(), point.id(), 2000L)).join();

        assertEquals(2, claims.countByLootPoint(point.id()).join());
    }

    @Test
    void deletesOneClaim() {
        LootPointRecord point = record();
        UUID playerId = UUID.randomUUID();
        lootPoints.insert(point).join();
        claims.insert(new ClaimRecord(playerId, point.id(), 1000L)).join();

        assertEquals(1, claims.delete(playerId, point.id()).join());
        assertEquals(0, claims.delete(playerId, point.id()).join());
        assertTrue(claims.findAllBlocking().isEmpty());
    }

    @Test
    void deletesAllClaimsForPlayer() {
        LootPointRecord firstPoint = record();
        LootPointRecord secondPoint = record();
        UUID playerId = UUID.randomUUID();
        UUID otherPlayerId = UUID.randomUUID();
        lootPoints.insert(firstPoint).join();
        lootPoints.insert(secondPoint).join();
        claims.insert(new ClaimRecord(playerId, firstPoint.id(), 1000L)).join();
        claims.insert(new ClaimRecord(playerId, secondPoint.id(), 2000L)).join();
        ClaimRecord retained = new ClaimRecord(otherPlayerId, firstPoint.id(), 3000L);
        claims.insert(retained).join();

        assertEquals(2, claims.deleteByPlayer(playerId).join());
        assertEquals(java.util.List.of(retained), claims.findAllBlocking());
    }

    @Test
    void deletesAllClaimsForLootPoint() {
        LootPointRecord firstPoint = record();
        LootPointRecord secondPoint = record();
        UUID playerId = UUID.randomUUID();
        lootPoints.insert(firstPoint).join();
        lootPoints.insert(secondPoint).join();
        claims.insert(new ClaimRecord(playerId, firstPoint.id(), 1000L)).join();
        claims.insert(new ClaimRecord(UUID.randomUUID(), firstPoint.id(), 2000L)).join();
        ClaimRecord retained = new ClaimRecord(playerId, secondPoint.id(), 3000L);
        claims.insert(retained).join();

        assertEquals(2, claims.deleteByLootPoint(firstPoint.id()).join());
        assertEquals(java.util.List.of(retained), claims.findAllBlocking());
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

    @Test
    void insertsLoadsAndCascadeDeletesShelfRewards() {
        LootPointRecord shelf = new LootPointRecord(
                UUID.randomUUID(), UUID.randomUUID(), LootPointType.SHELF,
                1, 2, 3, null, null, 1234L, UUID.randomUUID()
        );
        var rewards = java.util.List.of(
                new ShelfRewardItem(0, new byte[]{1, 2, 3}),
                new ShelfRewardItem(2, new byte[]{4, 5, 6})
        );

        assertTrue(lootPoints.insertShelf(shelf, rewards).join());
        UUID playerId = UUID.randomUUID();
        claims.insert(new ClaimRecord(playerId, shelf.id(), 5000L)).join();
        claims.delete(playerId, shelf.id()).join();
        var loaded = lootPoints.findAllShelfRewardsBlocking().get(shelf.id());
        assertEquals(java.util.List.of(0, 2), loaded.stream().map(ShelfRewardItem::slot).toList());
        assertTrue(java.util.Arrays.equals(new byte[]{1, 2, 3}, loaded.getFirst().serializedItem()));

        LootPointDeletion deletion = lootPoints.delete(shelf.id()).join().orElseThrow();
        assertEquals(2, deletion.shelfRewards().size());
        assertTrue(lootPoints.findAllShelfRewardsBlocking().isEmpty());
        assertTrue(lootPoints.restore(deletion).join());
        assertEquals(2, lootPoints.findAllShelfRewardsBlocking().get(shelf.id()).size());
    }

    @Test
    void validationReportsHealthyDatabaseIntegrity() {
        LootPointRecord point = record();
        lootPoints.insert(point).join();
        claims.insert(new ClaimRecord(UUID.randomUUID(), point.id(), 1234L)).join();

        assertTrue(validation.validateIntegrity().join().valid());
    }

    @Test
    void validationRepresentsForeignKeyFailures() {
        databaseManager.executeBlocking(connection -> {
            try (var pragma = connection.createStatement()) {
                pragma.execute("PRAGMA foreign_keys = OFF");
            }
            try (var statement = connection.prepareStatement("""
                    INSERT INTO loot_claims(player_uuid, loot_point_id, claimed_at) VALUES (?, ?, ?)
                    """)) {
                statement.setString(1, UUID.randomUUID().toString());
                statement.setString(2, UUID.randomUUID().toString());
                statement.setLong(3, 1234L);
                statement.executeUpdate();
            }
            return null;
        });

        var report = validation.validateIntegrity().join();
        assertFalse(report.valid());
        assertTrue(report.violations().stream().anyMatch(value -> value.contains("loot_claims")));
    }

    private void openDatabase() {
        databaseManager = new DatabaseManager(databasePath, Logger.getLogger("GameLootTest"));
        databaseManager.initialize(databasePath);
        lootPoints = new LootPointRepository(databaseManager);
        claims = new ClaimRepository(databaseManager);
        validation = new ValidationRepository(databaseManager);
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
