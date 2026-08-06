package io.github.blackshadowhrd.poiloot.model;

import org.bukkit.NamespacedKey;

import java.util.Objects;
import java.util.UUID;

public record LootPoint(UUID id, NamespacedKey lootTable, LootPointType type) {

    public LootPoint {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(lootTable, "lootTable");
        Objects.requireNonNull(type, "type");
    }
}
