package io.github.blackshadowhrd.gameloot.service;

import org.bukkit.plugin.Plugin;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class ClaimAdministrationService {

    private final Plugin plugin;
    private final ClaimService claimService;
    private final LootSessionService sessionService;

    public ClaimAdministrationService(
            Plugin plugin,
            ClaimService claimService,
            LootSessionService sessionService
    ) {
        this.plugin = plugin;
        this.claimService = claimService;
        this.sessionService = sessionService;
    }

    public int claimCount(UUID lootPointId) {
        return claimService.countPersistedClaims(lootPointId);
    }

    public boolean hasClaim(UUID playerId, UUID lootPointId) {
        return claimService.hasPersistedClaim(playerId, lootPointId);
    }

    public CompletableFuture<Integer> resetClaim(UUID playerId, UUID lootPointId) {
        return completeReset(claimService.resetClaim(playerId, lootPointId));
    }

    public CompletableFuture<Integer> resetPlayer(UUID playerId) {
        return completeReset(claimService.resetPlayer(playerId));
    }

    public CompletableFuture<Integer> resetLootPoint(UUID lootPointId) {
        return completeReset(claimService.resetLootPoint(lootPointId));
    }

    private CompletableFuture<Integer> completeReset(
            CompletableFuture<ClaimService.ClaimResetResult> reset
    ) {
        return reset.thenCompose(result -> runOnMainThread(() -> {
            sessionService.invalidateSessions(result.resetClaims());
            return result.deletedClaims();
        }));
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
}
