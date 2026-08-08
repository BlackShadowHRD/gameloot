package io.github.blackshadowhrd.gameloot.repository;

import io.github.blackshadowhrd.gameloot.database.DatabaseManager;
import io.github.blackshadowhrd.gameloot.repository.model.ClaimRecord;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class ClaimRepository {

    private final DatabaseManager databaseManager;

    public ClaimRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public List<ClaimRecord> findAllBlocking() {
        return databaseManager.executeBlocking(connection -> {
            List<ClaimRecord> claims = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM loot_claims");
                 ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    claims.add(new ClaimRecord(
                            java.util.UUID.fromString(result.getString("player_uuid")),
                            java.util.UUID.fromString(result.getString("loot_point_id")),
                            result.getLong("claimed_at")
                    ));
                }
            }
            return claims;
        });
    }

    public CompletableFuture<Boolean> insert(ClaimRecord claim) {
        return databaseManager.submit(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT OR IGNORE INTO loot_claims(player_uuid, loot_point_id, claimed_at)
                    VALUES (?, ?, ?)
                    """)) {
                statement.setString(1, claim.playerId().toString());
                statement.setString(2, claim.lootPointId().toString());
                statement.setLong(3, claim.claimedAt());
                return statement.executeUpdate() == 1;
            }
        });
    }

    public CompletableFuture<Integer> countByLootPoint(UUID lootPointId) {
        return databaseManager.submit(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT COUNT(*) FROM loot_claims WHERE loot_point_id = ?"
            )) {
                statement.setString(1, lootPointId.toString());
                try (ResultSet result = statement.executeQuery()) {
                    return result.next() ? result.getInt(1) : 0;
                }
            }
        });
    }

    public CompletableFuture<Integer> delete(UUID playerId, UUID lootPointId) {
        return deleteWhere(
                "DELETE FROM loot_claims WHERE player_uuid = ? AND loot_point_id = ?",
                playerId,
                lootPointId
        );
    }

    public CompletableFuture<Integer> deleteByPlayer(UUID playerId) {
        return deleteWhere("DELETE FROM loot_claims WHERE player_uuid = ?", playerId);
    }

    public CompletableFuture<Integer> deleteByLootPoint(UUID lootPointId) {
        return deleteWhere("DELETE FROM loot_claims WHERE loot_point_id = ?", lootPointId);
    }

    private CompletableFuture<Integer> deleteWhere(String sql, UUID... ids) {
        return databaseManager.submit(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                for (int index = 0; index < ids.length; index++) {
                    statement.setString(index + 1, ids[index].toString());
                }
                return statement.executeUpdate();
            }
        });
    }
}
