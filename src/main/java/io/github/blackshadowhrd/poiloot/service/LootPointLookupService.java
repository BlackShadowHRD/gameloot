package io.github.blackshadowhrd.poiloot.service;

import io.github.blackshadowhrd.poiloot.model.LootPoint;
import io.github.blackshadowhrd.poiloot.target.LootPointInspection;
import io.github.blackshadowhrd.poiloot.target.LootPointTarget;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.Container;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.minecart.StorageMinecart;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

public final class LootPointLookupService {

    private final NamespacedKey idKey;
    private final NamespacedKey lootTableKey;
    private final Logger logger;

    public LootPointLookupService(Plugin plugin) {
        idKey = new NamespacedKey(plugin, "loot_point_id");
        lootTableKey = new NamespacedKey(plugin, "loot_table");
        logger = plugin.getLogger();
    }

    public Optional<LootPointTarget> findTarget(Player player, double maxDistance) {
        return findSupportedTarget(player, maxDistance)
                .flatMap(target -> readLootPoint(target).map(target::withLootPoint));
    }

    public Optional<LootPointTarget> findTarget(Block block) {
        return supportedBlock(block)
                .flatMap(target -> readLootPoint(target).map(target::withLootPoint));
    }

    public Optional<LootPointTarget> findTarget(Entity entity) {
        return supportedEntity(entity)
                .flatMap(target -> readLootPoint(target).map(target::withLootPoint));
    }

    public Optional<LootPointInspection> inspectTarget(Player player, double maxDistance) {
        return findSupportedTarget(player, maxDistance).map(target -> new LootPointInspection(
                readLootPoint(target),
                target.type(),
                target.displayType(),
                target.location()
        ));
    }

    Optional<MutableLootPointTarget> findSupportedTarget(Player player, double maxDistance) {
        if (maxDistance < 0 || maxDistance > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("maxDistance must be between 0 and " + Integer.MAX_VALUE);
        }

        int targetDistance = (int) Math.floor(maxDistance);
        Entity targetEntity = player.getTargetEntity(targetDistance);
        Optional<MutableLootPointTarget> entityTarget = supportedEntity(targetEntity);
        if (entityTarget.isPresent()) {
            return entityTarget;
        }

        Block targetBlock = player.getTargetBlockExact(targetDistance);
        return supportedBlock(targetBlock);
    }

    private Optional<MutableLootPointTarget> supportedBlock(Block block) {
        if (block == null) {
            return Optional.empty();
        }

        BlockState state = block.getState();
        if (!(state instanceof Container container)) {
            return Optional.empty();
        }
        return Optional.of(new MutableBlockLootPointTarget(block, container, displayType(state, block.getType())));
    }

    private Optional<MutableLootPointTarget> supportedEntity(Entity entity) {
        if (entity instanceof StorageMinecart minecart) {
            return Optional.of(new MutableEntityLootPointTarget(minecart));
        }
        return Optional.empty();
    }

    private Optional<LootPoint> readLootPoint(MutableLootPointTarget target) {
        PersistentDataContainer data = target.data();
        if (!data.has(idKey) && !data.has(lootTableKey)) {
            return Optional.empty();
        }

        try {
            String storedId = data.get(idKey, PersistentDataType.STRING);
            String storedLootTable = data.get(lootTableKey, PersistentDataType.STRING);
            if (storedId == null || storedLootTable == null) {
                logMalformed(target, "missing ID or loot-table key");
                return Optional.empty();
            }

            UUID id = UUID.fromString(storedId);
            NamespacedKey lootTable = NamespacedKey.fromString(storedLootTable);
            if (lootTable == null) {
                logMalformed(target, "invalid loot-table key '" + storedLootTable + "'");
                return Optional.empty();
            }
            return Optional.of(new LootPoint(id, lootTable, target.type()));
        } catch (IllegalArgumentException exception) {
            logMalformed(target, exception.getMessage());
            return Optional.empty();
        }
    }

    private void logMalformed(MutableLootPointTarget target, String reason) {
        logger.warning("Malformed POILoot data on " + target.description() + ": " + reason);
    }

    private String displayType(BlockState state, Material material) {
        if (state instanceof Chest) {
            return "Chest";
        }
        if (state instanceof Barrel) {
            return "Barrel";
        }

        String[] words = material.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder displayName = new StringBuilder();
        for (String word : words) {
            if (!displayName.isEmpty()) {
                displayName.append(' ');
            }
            displayName.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return displayName.toString();
    }
}
