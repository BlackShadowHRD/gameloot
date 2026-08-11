package io.github.blackshadowhrd.gameloot.model;

import org.bukkit.NamespacedKey;

import java.util.UUID;

public record LootPointListEntry(
        UUID id,
        LootPointType persistedType,
        String targetType,
        String world,
        String resolvedWorldName,
        UUID worldUuid,
        int x,
        int y,
        int z,
        int persistedX,
        int persistedY,
        int persistedZ,
        UUID entityUuid,
        NamespacedKey lootTable,
        boolean shelf,
        String teleportCommand,
        boolean locationMayBeStale
) { }
