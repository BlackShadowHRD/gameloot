package io.github.blackshadowhrd.poiloot.service;

import io.github.blackshadowhrd.poiloot.repository.model.LootPointRecord;
import io.github.blackshadowhrd.poiloot.target.LootPointResolution;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public final class LootPointRegistrar {

    private static final double TARGET_DISTANCE = 6;

    private final Plugin plugin;
    private final LootPointLookupService lookupService;
    private final LootPointPersistenceService persistenceService;
    private final ClaimService claimService;

    public LootPointRegistrar(
            Plugin plugin,
            LootPointLookupService lookupService,
            LootPointPersistenceService persistenceService,
            ClaimService claimService
    ) {
        this.plugin = plugin;
        this.lookupService = lookupService;
        this.persistenceService = persistenceService;
        this.claimService = claimService;
    }

    public CompletableFuture<Result> register(Player player, NamespacedKey lootTable) {
        Optional<MutableLootPointTarget> located = lookupService.findSupportedTarget(player, TARGET_DISTANCE);
        if (located.isEmpty()) {
            return CompletableFuture.completedFuture(Result.INVALID_TARGET);
        }

        MutableLootPointTarget target = located.get();
        if (lookupService.hasMarker(target)) {
            return CompletableFuture.completedFuture(Result.ALREADY_REGISTERED);
        }

        UUID id = UUID.randomUUID();
        LootPointRecord record = lookupService.record(target, id, lootTable, player.getUniqueId());
        return persistenceService.insert(record).thenCompose(inserted -> {
            if (!inserted) {
                return CompletableFuture.completedFuture(Result.PERSISTENCE_FAILURE);
            }
            return runOnMainThread(() -> lookupService.refresh(target)
                    .map(current -> writeMarker(current, id))
                    .orElse(false)).thenCompose(written -> {
                if (written) {
                    return CompletableFuture.completedFuture(Result.REGISTERED);
                }
                return persistenceService.delete(id).handle((deleted, exception) -> {
                    if (exception != null || deleted.isEmpty()) {
                        plugin.getLogger().log(Level.SEVERE,
                                "Failed to compensate database insert for loot point " + id,
                                exception);
                    }
                    return Result.PERSISTENCE_FAILURE;
                });
            });
        }).exceptionally(exception -> {
            plugin.getLogger().log(Level.SEVERE, "Failed to register loot point", exception);
            return Result.PERSISTENCE_FAILURE;
        });
    }

    public CompletableFuture<DeregisterResult> deregister(Player player) {
        Optional<MutableLootPointTarget> located = lookupService.findSupportedTarget(player, TARGET_DISTANCE);
        if (located.isEmpty()) {
            return CompletableFuture.completedFuture(DeregisterResult.INVALID_TARGET);
        }

        MutableLootPointTarget target = located.get();
        LootPointResolution resolution = lookupService.resolveTarget(target);
        if (!resolution.marked()) {
            return CompletableFuture.completedFuture(DeregisterResult.NOT_REGISTERED);
        }

        return resolution.target().thenCompose(resolved -> {
            if (resolved.isEmpty()) {
                return CompletableFuture.completedFuture(DeregisterResult.MISSING_DATABASE_RECORD);
            }
            UUID id = resolved.get().lootPoint().id();
            return persistenceService.delete(id).thenCompose(deleted -> {
                if (deleted.isEmpty()) {
                    return CompletableFuture.completedFuture(DeregisterResult.MISSING_DATABASE_RECORD);
                }
                return runOnMainThread(() -> lookupService.refresh(target)
                        .map(this::removeMarker)
                        .orElse(false)).thenCompose(removed -> {
                    if (removed) {
                        claimService.removeClaimsForLootPoint(id);
                        return CompletableFuture.completedFuture(DeregisterResult.DEREGISTERED);
                    }
                    return persistenceService.restore(deleted.get()).handle((restored, exception) -> {
                        if (exception != null || !restored) {
                            plugin.getLogger().log(Level.SEVERE,
                                    "Failed to compensate deregistration for loot point " + id,
                                    exception);
                        }
                        return DeregisterResult.PERSISTENCE_FAILURE;
                    });
                });
            });
        }).exceptionally(exception -> {
            plugin.getLogger().log(Level.SEVERE, "Failed to deregister loot point", exception);
            return DeregisterResult.PERSISTENCE_FAILURE;
        });
    }

    private boolean writeMarker(MutableLootPointTarget target, UUID id) {
        try {
            lookupService.writeMarker(target, id);
            return target.persist();
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to write PDC marker for loot point " + id, exception);
            return false;
        }
    }

    private boolean removeMarker(MutableLootPointTarget target) {
        try {
            lookupService.removeMarker(target);
            return target.persist();
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to remove loot-point PDC marker", exception);
            return false;
        }
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

    public enum Result {
        REGISTERED,
        ALREADY_REGISTERED,
        INVALID_TARGET,
        PERSISTENCE_FAILURE
    }

    public enum DeregisterResult {
        DEREGISTERED,
        NOT_REGISTERED,
        INVALID_TARGET,
        MISSING_DATABASE_RECORD,
        PERSISTENCE_FAILURE
    }
}
