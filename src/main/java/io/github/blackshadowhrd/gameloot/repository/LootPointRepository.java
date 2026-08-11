package io.github.blackshadowhrd.gameloot.repository;

import io.github.blackshadowhrd.gameloot.database.DatabaseManager;
import io.github.blackshadowhrd.gameloot.model.LootPointType;
import io.github.blackshadowhrd.gameloot.repository.model.LootPointRecord;
import io.github.blackshadowhrd.gameloot.repository.model.LootPointDeletion;
import io.github.blackshadowhrd.gameloot.repository.model.ClaimRecord;
import io.github.blackshadowhrd.gameloot.repository.model.ShelfRewardItem;
import org.bukkit.NamespacedKey;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class LootPointRepository {

    private static final String INSERT_SQL = """
            INSERT INTO loot_points(
                id, world_uuid, target_type, x, y, z, entity_uuid, loot_table, created_at, created_by
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private final DatabaseManager databaseManager;

    public LootPointRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public List<LootPointRecord> findAllBlocking() {
        return databaseManager.executeBlocking(this::findAll);
    }

    public CompletableFuture<List<LootPointRecord>> findAllOrdered() {
        return databaseManager.submit(connection -> findAll(connection, """
                SELECT * FROM loot_points ORDER BY world_uuid, x, z, y, id
                """));
    }

    public CompletableFuture<Optional<LootPointRecord>> findById(UUID id) {
        return databaseManager.submit(connection -> findById(connection, id));
    }

    public CompletableFuture<Boolean> insert(LootPointRecord record) {
        return databaseManager.submit(connection -> insert(connection, record));
    }

    public CompletableFuture<Boolean> insertShelf(LootPointRecord record, List<ShelfRewardItem> rewards) {
        return databaseManager.submit(connection -> {
            connection.setAutoCommit(false);
            try {
                if (!insert(connection, record)) {
                    connection.rollback();
                    return false;
                }
                insertShelfRewards(connection, record.id(), rewards);
                connection.commit();
                return true;
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        });
    }

    public Map<UUID, List<ShelfRewardItem>> findAllShelfRewardsBlocking() {
        return databaseManager.executeBlocking(connection -> {
            Map<UUID, List<ShelfRewardItem>> rewards = new HashMap<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT loot_point_id, slot, serialized_item FROM shelf_loot_items ORDER BY slot"
            ); ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    UUID id = UUID.fromString(result.getString("loot_point_id"));
                    rewards.computeIfAbsent(id, ignored -> new ArrayList<>()).add(new ShelfRewardItem(
                            result.getInt("slot"), result.getBytes("serialized_item")
                    ));
                }
            }
            return rewards;
        });
    }

    public CompletableFuture<Optional<LootPointDeletion>> delete(UUID id) {
        return databaseManager.submit(connection -> {
            connection.setAutoCommit(false);
            try {
                Optional<LootPointRecord> existing = findById(connection, id);
                if (existing.isEmpty()) {
                    connection.rollback();
                    return Optional.empty();
                }
                List<ClaimRecord> claims = findClaims(connection, id);
                List<ShelfRewardItem> rewards = findShelfRewards(connection, id);
                try (PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM loot_points WHERE id = ?"
                )) {
                    statement.setString(1, id.toString());
                    statement.executeUpdate();
                }
                connection.commit();
                return Optional.of(new LootPointDeletion(existing.get(), claims, rewards));
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        });
    }

    public CompletableFuture<Boolean> restore(LootPointDeletion deletion) {
        return databaseManager.submit(connection -> {
            connection.setAutoCommit(false);
            try {
                if (!insert(connection, deletion.lootPoint())) {
                    connection.rollback();
                    return false;
                }
                insertShelfRewards(connection, deletion.lootPoint().id(), deletion.shelfRewards());
                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT OR IGNORE INTO loot_claims(player_uuid, loot_point_id, claimed_at)
                        VALUES (?, ?, ?)
                        """)) {
                    for (ClaimRecord claim : deletion.claims()) {
                        statement.setString(1, claim.playerId().toString());
                        statement.setString(2, claim.lootPointId().toString());
                        statement.setLong(3, claim.claimedAt());
                        statement.addBatch();
                    }
                    statement.executeBatch();
                }
                connection.commit();
                return true;
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        });
    }

    private List<LootPointRecord> findAll(Connection connection) throws SQLException {
        return findAll(connection, "SELECT * FROM loot_points");
    }

    private List<LootPointRecord> findAll(Connection connection, String sql) throws SQLException {
        List<LootPointRecord> records = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                records.add(readRecord(result));
            }
        }
        return records;
    }

    private Optional<LootPointRecord> findById(Connection connection, UUID id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT * FROM loot_points WHERE id = ?"
        )) {
            statement.setString(1, id.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(readRecord(result)) : Optional.empty();
            }
        }
    }

    private boolean insert(Connection connection, LootPointRecord record) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_SQL)) {
            setRecord(statement, record);
            return statement.executeUpdate() == 1;
        } catch (SQLException exception) {
            if (exception.getErrorCode() == 19) {
                return false;
            }
            throw exception;
        }
    }

    private List<ClaimRecord> findClaims(Connection connection, UUID lootPointId) throws SQLException {
        List<ClaimRecord> claims = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT player_uuid, loot_point_id, claimed_at FROM loot_claims WHERE loot_point_id = ?"
        )) {
            statement.setString(1, lootPointId.toString());
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    claims.add(new ClaimRecord(
                            UUID.fromString(result.getString("player_uuid")),
                            UUID.fromString(result.getString("loot_point_id")),
                            result.getLong("claimed_at")
                    ));
                }
            }
        }
        return claims;
    }

    private void setRecord(PreparedStatement statement, LootPointRecord record) throws SQLException {
        statement.setString(1, record.id().toString());
        statement.setString(2, record.worldUuid().toString());
        statement.setString(3, record.targetType().name());
        statement.setInt(4, record.x());
        statement.setInt(5, record.y());
        statement.setInt(6, record.z());
        statement.setString(7, record.entityUuid() == null ? null : record.entityUuid().toString());
        if (record.lootTable() == null) statement.setNull(8, Types.VARCHAR);
        else statement.setString(8, record.lootTable().asString());
        statement.setLong(9, record.createdAt());
        statement.setString(10, record.createdBy() == null ? null : record.createdBy().toString());
    }

    private LootPointRecord readRecord(ResultSet result) throws SQLException {
        String entityId = result.getString("entity_uuid");
        String createdBy = result.getString("created_by");
        String storedLootTable = result.getString("loot_table");
        NamespacedKey lootTable = storedLootTable == null ? null : NamespacedKey.fromString(storedLootTable);
        if (storedLootTable != null && lootTable == null) {
            throw new SQLException("Invalid loot-table key for loot point " + result.getString("id"));
        }
        return new LootPointRecord(
                UUID.fromString(result.getString("id")),
                UUID.fromString(result.getString("world_uuid")),
                LootPointType.valueOf(result.getString("target_type")),
                result.getInt("x"),
                result.getInt("y"),
                result.getInt("z"),
                entityId == null ? null : UUID.fromString(entityId),
                lootTable,
                result.getLong("created_at"),
                createdBy == null ? null : UUID.fromString(createdBy)
        );
    }

    private void insertShelfRewards(
            Connection connection,
            UUID lootPointId,
            List<ShelfRewardItem> rewards
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO shelf_loot_items(loot_point_id, slot, serialized_item) VALUES (?, ?, ?)
                """)) {
            for (ShelfRewardItem reward : rewards) {
                statement.setString(1, lootPointId.toString());
                statement.setInt(2, reward.slot());
                statement.setBytes(3, reward.serializedItem());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private List<ShelfRewardItem> findShelfRewards(Connection connection, UUID lootPointId) throws SQLException {
        List<ShelfRewardItem> rewards = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT slot, serialized_item FROM shelf_loot_items WHERE loot_point_id = ? ORDER BY slot
                """)) {
            statement.setString(1, lootPointId.toString());
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    rewards.add(new ShelfRewardItem(result.getInt("slot"), result.getBytes("serialized_item")));
                }
            }
        }
        return rewards;
    }
}
