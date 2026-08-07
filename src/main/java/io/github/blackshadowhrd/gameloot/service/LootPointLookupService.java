package io.github.blackshadowhrd.gameloot.service;

import io.github.blackshadowhrd.gameloot.model.LootPoint;
import io.github.blackshadowhrd.gameloot.repository.model.LootPointRecord;
import io.github.blackshadowhrd.gameloot.target.LootPointInspection;
import io.github.blackshadowhrd.gameloot.target.LootPointResolution;
import io.github.blackshadowhrd.gameloot.target.LootPointTarget;
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
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class LootPointLookupService {

    private final NamespacedKey idKey;
    private final NamespacedKey lootTableKey;
    private final NamespacedKey legacyIdKey = NamespacedKey.fromString("poiloot:loot_point_id");
    private final NamespacedKey legacyLootTableKey = NamespacedKey.fromString("poiloot:loot_table");
    private final Logger logger;
    private final Plugin plugin;
    private final LootPointPersistenceService persistenceService;

    public LootPointLookupService(Plugin plugin, LootPointPersistenceService persistenceService) {
        this.plugin = plugin;
        this.persistenceService = persistenceService;
        idKey = new NamespacedKey(plugin, "loot_point_id");
        lootTableKey = new NamespacedKey(plugin, "loot_table");
        logger = plugin.getLogger();
    }

    public LootPointResolution resolveTarget(Block block) {
        return supportedBlock(block).map(this::resolveTarget)
                .orElseGet(() -> unmarkedResolution());
    }

    public LootPointResolution resolveTarget(Entity entity) {
        return supportedEntity(entity).map(this::resolveTarget)
                .orElseGet(() -> unmarkedResolution());
    }

    public CompletableFuture<Optional<LootPointInspection>> inspectTarget(Player player, double maxDistance) {
        Optional<MutableLootPointTarget> supportedTarget = findSupportedTarget(player, maxDistance);
        if (supportedTarget.isEmpty()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        MutableLootPointTarget target = supportedTarget.get();
        LootPointResolution resolution = resolveTarget(target);
        if (!resolution.marked()) {
            return CompletableFuture.completedFuture(Optional.of(inspection(target, Optional.empty(), false)));
        }
        return resolution.target().thenApply(resolved -> Optional.of(inspection(
                target,
                resolved.map(LootPointTarget::lootPoint),
                true
        )));
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

    Optional<UUID> readMarker(MutableLootPointTarget target) {
        PersistentDataContainer data = target.data();
        NamespacedKey storedKey = markerKey(data);
        if (storedKey == null) {
            return Optional.empty();
        }

        try {
            String storedId = data.get(storedKey, PersistentDataType.STRING);
            if (storedId == null) {
                logMalformed(target, "loot-point ID is not stored as a string");
                return Optional.empty();
            }
            return Optional.of(UUID.fromString(storedId));
        } catch (IllegalArgumentException exception) {
            logMalformed(target, exception.getMessage());
            return Optional.empty();
        }
    }

    boolean hasMarker(MutableLootPointTarget target) {
        return markerKey(target.data()) != null;
    }

    void writeMarker(MutableLootPointTarget target, UUID id) {
        target.data().set(idKey, PersistentDataType.STRING, id.toString());
        target.data().remove(lootTableKey);
        target.data().remove(legacyIdKey);
        target.data().remove(legacyLootTableKey);
    }

    void removeMarker(MutableLootPointTarget target) {
        target.data().remove(idKey);
        target.data().remove(lootTableKey);
        target.data().remove(legacyIdKey);
        target.data().remove(legacyLootTableKey);
    }

    LootPointResolution resolveTarget(MutableLootPointTarget target) {
        PersistentDataContainer data = target.data();
        if (markerKey(data) == null) {
            return unmarkedResolution();
        }

        Optional<UUID> marker = readMarker(target);
        if (marker.isEmpty()) {
            return new LootPointResolution(true, CompletableFuture.completedFuture(Optional.empty()));
        }

        UUID id = marker.get();
        Optional<LootPointRecord> cached = persistenceService.find(id);
        if (cached.isPresent()) {
            if (!matches(target, cached.get())) {
                logger.severe("Loot point marker " + id + " on " + target.description()
                        + " does not match its database target metadata");
                return new LootPointResolution(true, CompletableFuture.completedFuture(Optional.empty()));
            }
            migratePdcMetadata(target, id);
            return resolved(target, cached.get().lootPoint());
        }

        String legacyLootTable;
        try {
            NamespacedKey storedLootTableKey = data.has(lootTableKey) ? lootTableKey : legacyLootTableKey;
            legacyLootTable = data.get(storedLootTableKey, PersistentDataType.STRING);
        } catch (IllegalArgumentException exception) {
            logMalformed(target, "legacy loot-table value has the wrong PDC type");
            return new LootPointResolution(true, CompletableFuture.completedFuture(Optional.empty()));
        }
        NamespacedKey key = legacyLootTable == null ? null : NamespacedKey.fromString(legacyLootTable);
        key = migrateLootTableNamespace(key);
        if (key == null) {
            logger.severe("Loot point marker " + id + " on " + target.description()
                    + " has no database record and no valid legacy loot-table key");
            return new LootPointResolution(true, CompletableFuture.completedFuture(Optional.empty()));
        }

        LootPointRecord legacyRecord = record(target, id, key, null);
        String targetDescription = target.description();
        CompletableFuture<Optional<LootPointTarget>> migration = persistenceService.migrateLegacy(legacyRecord)
                .thenCompose(record -> runOnMainThread(() -> record.flatMap(found -> {
                    MutableLootPointTarget current = refresh(target).orElse(target);
                    if (!matches(current, found)) {
                        logger.severe("Legacy loot point " + id + " on " + current.description()
                                + " conflicts with its existing database target metadata");
                        return Optional.empty();
                    }
                    migratePdcMetadata(current, id);
                    logger.info("Migrated legacy loot point " + id + " on " + current.description());
                    return Optional.of(current.withLootPoint(found.lootPoint()));
                })))
                .exceptionally(exception -> {
                    logger.log(Level.SEVERE, "Failed to migrate legacy loot point " + id + " on "
                            + targetDescription, exception);
                    return Optional.empty();
                });
        return new LootPointResolution(true, migration);
    }

    LootPointRecord record(
            MutableLootPointTarget target,
            UUID id,
            NamespacedKey lootTable,
            UUID createdBy
    ) {
        Location location = target.location();
        UUID entityId = target instanceof MutableEntityLootPointTarget entityTarget
                ? entityTarget.entity().getUniqueId()
                : null;
        return new LootPointRecord(
                id,
                location.getWorld().getUID(),
                target.type(),
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ(),
                entityId,
                lootTable,
                System.currentTimeMillis(),
                createdBy
        );
    }

    private void migratePdcMetadata(MutableLootPointTarget target, UUID id) {
        PersistentDataContainer data = target.data();
        if (!data.has(idKey)
                || data.has(lootTableKey)
                || data.has(legacyIdKey)
                || data.has(legacyLootTableKey)) {
            writeMarker(target, id);
            target.persist();
        }
    }

    private NamespacedKey markerKey(PersistentDataContainer data) {
        if (data.has(idKey)) {
            return idKey;
        }
        return data.has(legacyIdKey) ? legacyIdKey : null;
    }

    private NamespacedKey migrateLootTableNamespace(NamespacedKey key) {
        if (key != null && key.namespace().equals("poiloot")) {
            return new NamespacedKey("gameloot", key.getKey());
        }
        return key;
    }

    Optional<MutableLootPointTarget> refresh(MutableLootPointTarget target) {
        if (target instanceof MutableBlockLootPointTarget blockTarget) {
            return supportedBlock(blockTarget.block());
        }
        if (target instanceof MutableEntityLootPointTarget entityTarget) {
            return supportedEntity(entityTarget.entity());
        }
        return Optional.empty();
    }

    private boolean matches(MutableLootPointTarget target, LootPointRecord record) {
        Location location = target.location();
        if (!record.worldUuid().equals(location.getWorld().getUID())
                || record.targetType() != target.type()
                || record.x() != location.getBlockX()
                || record.y() != location.getBlockY()
                || record.z() != location.getBlockZ()) {
            return false;
        }
        if (target instanceof MutableEntityLootPointTarget entityTarget) {
            return entityTarget.entity().getUniqueId().equals(record.entityUuid());
        }
        return record.entityUuid() == null;
    }

    private LootPointInspection inspection(
            MutableLootPointTarget target,
            Optional<LootPoint> lootPoint,
            boolean markerPresent
    ) {
        return new LootPointInspection(
                lootPoint,
                markerPresent,
                target.type(),
                target.displayType(),
                target.location()
        );
    }

    private LootPointResolution resolved(MutableLootPointTarget target, LootPoint lootPoint) {
        return new LootPointResolution(
                true,
                CompletableFuture.completedFuture(Optional.of(target.withLootPoint(lootPoint)))
        );
    }

    private LootPointResolution unmarkedResolution() {
        return new LootPointResolution(false, CompletableFuture.completedFuture(Optional.empty()));
    }

    private <T> CompletableFuture<T> runOnMainThread(java.util.function.Supplier<T> supplier) {
        CompletableFuture<T> future = new CompletableFuture<>();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            try {
                future.complete(supplier.get());
            } catch (RuntimeException exception) {
                future.completeExceptionally(exception);
            }
        });
        return future;
    }

    private void logMalformed(MutableLootPointTarget target, String reason) {
        logger.warning("Malformed GameLoot data on " + target.description() + ": " + reason);
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
