package io.github.blackshadowhrd.gameloot.repository;

import io.github.blackshadowhrd.gameloot.database.DatabaseManager;
import io.github.blackshadowhrd.gameloot.model.LootPointType;
import io.github.blackshadowhrd.gameloot.validation.DatabaseIntegrityReport;
import org.bukkit.NamespacedKey;

import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class ValidationRepository {
    private final DatabaseManager databaseManager;

    public ValidationRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public CompletableFuture<DatabaseIntegrityReport> validateIntegrity() {
        return databaseManager.submit(connection -> {
            List<String> violations = new ArrayList<>();
            try (Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery("PRAGMA foreign_key_check")) {
                while (result.next()) {
                    violations.add(result.getString("table") + " row " + result.getLong("rowid")
                            + " references " + result.getString("parent"));
                }
            }
            try (Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery("""
                         SELECT id, world_uuid, target_type, entity_uuid, loot_table FROM loot_points
                         """)) {
                while (result.next()) validateLootPointRow(result, violations);
            }
            try (Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery("SELECT player_uuid, loot_point_id FROM loot_claims")) {
                while (result.next()) {
                    validateUuid(result.getString("player_uuid"), "claim player UUID", violations);
                    validateUuid(result.getString("loot_point_id"), "claim loot-point UUID", violations);
                }
            }
            try (Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery("""
                         SELECT s.loot_point_id
                         FROM shelf_loot_items s
                         JOIN loot_points p ON p.id = s.loot_point_id
                         WHERE p.target_type <> 'SHELF'
                         """)) {
                while (result.next()) {
                    violations.add("shelf reward belongs to non-shelf loot point "
                            + result.getString("loot_point_id"));
                }
            }
            return new DatabaseIntegrityReport(violations);
        });
    }

    private void validateLootPointRow(ResultSet result, List<String> violations) throws java.sql.SQLException {
        String id = result.getString("id");
        validateUuid(id, "loot-point UUID", violations);
        validateUuid(result.getString("world_uuid"), "world UUID for " + id, violations);
        String targetType = result.getString("target_type");
        try {
            LootPointType.valueOf(targetType);
        } catch (IllegalArgumentException | NullPointerException exception) {
            violations.add("unknown target type '" + targetType + "' for " + id);
        }
        String entityUuid = result.getString("entity_uuid");
        if (entityUuid != null) validateUuid(entityUuid, "entity UUID for " + id, violations);
        String lootTable = result.getString("loot_table");
        if (lootTable != null) {
            try {
                if (NamespacedKey.fromString(lootTable) == null) {
                    violations.add("invalid loot-table key '" + lootTable + "' for " + id);
                }
            } catch (IllegalArgumentException exception) {
                violations.add("invalid loot-table key '" + lootTable + "' for " + id);
            }
        }
    }

    private void validateUuid(String value, String field, List<String> violations) {
        try {
            UUID.fromString(value);
        } catch (IllegalArgumentException | NullPointerException exception) {
            violations.add("invalid " + field + ": " + value);
        }
    }
}
