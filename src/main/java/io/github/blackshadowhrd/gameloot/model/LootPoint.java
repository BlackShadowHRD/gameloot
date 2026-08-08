package io.github.blackshadowhrd.gameloot.model;

import org.bukkit.NamespacedKey;

import java.util.Objects;
import java.util.UUID;

public record LootPoint(UUID id, NamespacedKey lootTable, LootPointType type) {

    public LootPoint {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        if ((type == LootPointType.SHELF) != (lootTable == null)) {
            throw new IllegalArgumentException("Only shelves may omit a loot table");
        }
    }
}
