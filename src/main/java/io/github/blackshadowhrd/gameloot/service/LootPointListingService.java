package io.github.blackshadowhrd.gameloot.service;

import io.github.blackshadowhrd.gameloot.model.LootPointListEntry;
import io.github.blackshadowhrd.gameloot.model.LootPointType;
import io.github.blackshadowhrd.gameloot.repository.LootPointRepository;
import io.github.blackshadowhrd.gameloot.repository.model.LootPointRecord;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class LootPointListingService {
    private final Plugin plugin;
    private final Server server;
    private final LootPointRepository repository;
    private final LootPointTargetResolver targetResolver;

    public LootPointListingService(Plugin plugin, LootPointRepository repository, LootPointTargetResolver targetResolver) {
        this.plugin = plugin;
        server = plugin.getServer();
        this.repository = repository;
        this.targetResolver = targetResolver;
    }

    public CompletableFuture<List<LootPointListEntry>> list() {
        return repository.findAllOrdered().thenCompose(records -> runOnMainThread(() ->
                records.stream().map(this::entry).toList()));
    }

    private LootPointListEntry entry(LootPointRecord record) {
        World world = server.getWorld(record.worldUuid());
        String worldName = world == null ? record.worldUuid() + " (unavailable)" : world.getName();
        int x = record.x();
        int y = record.y();
        int z = record.z();
        boolean stale = record.targetType() == LootPointType.CHEST_MINECART;
        String displayType = persistedDisplayType(record.targetType());

        if (world != null && record.targetType() == LootPointType.CHEST_MINECART) {
            Entity entity = record.entityUuid() == null ? null : world.getEntity(record.entityUuid());
            Optional<MutableLootPointTarget> target = targetResolver.resolve(entity);
            if (target.isPresent()) {
                x = target.get().location().getBlockX();
                y = target.get().location().getBlockY();
                z = target.get().location().getBlockZ();
                displayType = target.get().displayType();
                stale = false;
            }
        } else if (world != null && world.isChunkLoaded(Math.floorDiv(x, 16), Math.floorDiv(z, 16))) {
            Optional<MutableLootPointTarget> target = targetResolver.resolve(world.getBlockAt(x, y, z));
            if (target.isPresent() && target.get().type() == record.targetType()) {
                displayType = target.get().displayType();
            }
        }

        int teleportY = record.targetType() == LootPointType.CHEST_MINECART ? y : y + 1;
        String teleport = world == null ? null : "/execute in " + world.getKey().asString()
                + " run tp @s " + x + " " + teleportY + " " + z;
        return new LootPointListEntry(record.id(), record.targetType(), displayType, worldName,
                world == null ? null : world.getName(), record.worldUuid(), x, y, z,
                record.x(), record.y(), record.z(), record.entityUuid(), record.lootTable(),
                record.targetType() == LootPointType.SHELF, teleport, stale);
    }

    private String persistedDisplayType(LootPointType type) {
        return switch (type) {
            case BLOCK_CONTAINER -> "Block Container";
            case CHEST_MINECART -> "Chest Minecart";
            case SHELF -> "Shelf";
        };
    }

    private <T> CompletableFuture<T> runOnMainThread(java.util.function.Supplier<T> supplier) {
        CompletableFuture<T> future = new CompletableFuture<>();
        server.getScheduler().runTask(plugin, () -> {
            try { future.complete(supplier.get()); }
            catch (RuntimeException exception) { future.completeExceptionally(exception); }
        });
        return future;
    }
}
