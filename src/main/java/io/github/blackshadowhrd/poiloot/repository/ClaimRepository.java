package io.github.blackshadowhrd.poiloot.repository;

import io.github.blackshadowhrd.poiloot.database.DatabaseManager;
import io.github.blackshadowhrd.poiloot.repository.model.ClaimRecord;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
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
}
