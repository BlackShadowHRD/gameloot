package io.github.blackshadowhrd.gameloot.service;

import io.github.blackshadowhrd.gameloot.database.DatabaseManager;
import io.github.blackshadowhrd.gameloot.model.LootPointType;
import io.github.blackshadowhrd.gameloot.repository.ClaimRepository;
import io.github.blackshadowhrd.gameloot.repository.LootPointRepository;
import io.github.blackshadowhrd.gameloot.repository.model.LootPointRecord;
import org.bukkit.NamespacedKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaimServiceIntegrationTest {

    @TempDir
    Path temporaryDirectory;

    private DatabaseManager databaseManager;
    private LootPointRepository lootPoints;
    private ClaimService claims;

    @BeforeEach
    void setUp() {
        Path databasePath = temporaryDirectory.resolve("gameloot.db");
        databaseManager = new DatabaseManager(databasePath, Logger.getLogger("GameLootTest"));
        databaseManager.initialize(databasePath);
        lootPoints = new LootPointRepository(databaseManager);
        claims = new ClaimService(new ClaimRepository(databaseManager), Logger.getLogger("GameLootTest"));
        claims.load();
    }

    @AfterEach
    void tearDown() {
        databaseManager.shutdown();
    }

    @Test
    void keepsCacheConsistentWhenResettingOneClaim() {
        LootPointRecord point = insertPoint();
        UUID playerId = UUID.randomUUID();
        assertTrue(claims.markClaimed(playerId, point.id()).join());

        ClaimService.ClaimResetResult result = claims.resetClaim(playerId, point.id()).join();

        assertEquals(1, result.deletedClaims());
        assertFalse(claims.isClaimed(playerId, point.id()));
        assertEquals(0, claims.countPersistedClaims(point.id()));
        assertTrue(claims.markClaimed(playerId, point.id()).join());
    }

    @Test
    void keepsCacheConsistentWhenResettingPlayer() {
        LootPointRecord firstPoint = insertPoint();
        LootPointRecord secondPoint = insertPoint();
        UUID playerId = UUID.randomUUID();
        UUID otherPlayerId = UUID.randomUUID();
        claims.markClaimed(playerId, firstPoint.id()).join();
        claims.markClaimed(playerId, secondPoint.id()).join();
        claims.markClaimed(otherPlayerId, firstPoint.id()).join();

        ClaimService.ClaimResetResult result = claims.resetPlayer(playerId).join();

        assertEquals(2, result.deletedClaims());
        assertFalse(claims.isClaimed(playerId, firstPoint.id()));
        assertFalse(claims.isClaimed(playerId, secondPoint.id()));
        assertTrue(claims.isClaimed(otherPlayerId, firstPoint.id()));
    }

    @Test
    void keepsCacheConsistentWhenResettingLootPoint() {
        LootPointRecord point = insertPoint();
        LootPointRecord retainedPoint = insertPoint();
        UUID playerId = UUID.randomUUID();
        claims.markClaimed(playerId, point.id()).join();
        claims.markClaimed(UUID.randomUUID(), point.id()).join();
        claims.markClaimed(playerId, retainedPoint.id()).join();

        ClaimService.ClaimResetResult result = claims.resetLootPoint(point.id()).join();

        assertEquals(2, result.deletedClaims());
        assertEquals(0, claims.countPersistedClaims(point.id()));
        assertTrue(claims.isClaimed(playerId, retainedPoint.id()));
    }

    @Test
    void shelfClaimsArePerPlayerAndReloadFromPersistence() {
        LootPointRecord shelf = new LootPointRecord(
                UUID.randomUUID(), UUID.randomUUID(), LootPointType.SHELF,
                1, 2, 3, null, null, 1234L, UUID.randomUUID()
        );
        lootPoints.insertShelf(shelf, java.util.List.of(
                new io.github.blackshadowhrd.gameloot.repository.model.ShelfRewardItem(0, new byte[]{1})
        )).join();
        UUID firstPlayer = UUID.randomUUID();
        UUID secondPlayer = UUID.randomUUID();

        assertTrue(claims.markClaimed(firstPlayer, shelf.id()).join());
        assertFalse(claims.markClaimed(firstPlayer, shelf.id()).join());
        assertTrue(claims.markClaimed(secondPlayer, shelf.id()).join());

        claims.load();
        assertTrue(claims.isClaimed(firstPlayer, shelf.id()));
        assertTrue(claims.isClaimed(secondPlayer, shelf.id()));
    }

    private LootPointRecord insertPoint() {
        LootPointRecord point = new LootPointRecord(
                UUID.randomUUID(),
                UUID.randomUUID(),
                LootPointType.BLOCK_CONTAINER,
                10,
                64,
                -20,
                null,
                NamespacedKey.minecraft("chests/simple_dungeon"),
                1234L,
                UUID.randomUUID()
        );
        lootPoints.insert(point).join();
        return point;
    }
}
