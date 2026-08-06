package io.github.blackshadowhrd.poiloot.repository.model;

import io.github.blackshadowhrd.poiloot.model.LootPoint;
import io.github.blackshadowhrd.poiloot.model.LootPointType;
import org.bukkit.NamespacedKey;

import java.util.UUID;

public record LootPointRecord(
        UUID id,
        UUID worldUuid,
        LootPointType targetType,
        int x,
        int y,
        int z,
        UUID entityUuid,
        NamespacedKey lootTable,
        long createdAt,
        UUID createdBy
) {

    public LootPoint lootPoint() {
        return new LootPoint(id, lootTable, targetType);
    }
}
