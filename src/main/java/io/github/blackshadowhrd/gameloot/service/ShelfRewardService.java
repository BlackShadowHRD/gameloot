package io.github.blackshadowhrd.gameloot.service;

import io.github.blackshadowhrd.gameloot.model.LootPoint;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class ShelfRewardService {

    private final Plugin plugin;
    private final LootPointPersistenceService persistenceService;
    private final ClaimService claimService;

    public ShelfRewardService(
            Plugin plugin,
            LootPointPersistenceService persistenceService,
            ClaimService claimService
    ) {
        this.plugin = plugin;
        this.persistenceService = persistenceService;
        this.claimService = claimService;
    }

    public int rewardSlots(LootPoint lootPoint) {
        return persistenceService.shelfRewards(lootPoint.id()).size();
    }

    public CompletableFuture<ClaimResult> claim(Player player, LootPoint lootPoint) {
        if (claimService.isClaimed(player.getUniqueId(), lootPoint.id())) {
            return CompletableFuture.completedFuture(ClaimResult.ALREADY_CLAIMED);
        }
        List<ItemStack> rewards = deserialize(lootPoint);
        if (rewards.isEmpty()) return CompletableFuture.completedFuture(ClaimResult.MISSING_REWARD);
        if (plan(player.getInventory().getStorageContents(), rewards).isEmpty()) {
            return CompletableFuture.completedFuture(ClaimResult.INSUFFICIENT_SPACE);
        }

        return claimService.markClaimed(player.getUniqueId(), lootPoint.id()).thenCompose(persisted -> {
            if (!persisted) {
                return CompletableFuture.completedFuture(claimService.hasPersistedClaim(
                        player.getUniqueId(), lootPoint.id())
                        ? ClaimResult.ALREADY_CLAIMED : ClaimResult.PERSISTENCE_FAILURE);
            }
            return runOnMainThread(() -> plan(player.getInventory().getStorageContents(), rewards))
                    .thenCompose(result -> {
                        if (result.isEmpty()) return compensate(player, lootPoint, ClaimResult.INSUFFICIENT_SPACE);
                        try {
                            player.getInventory().setStorageContents(result.get());
                            return CompletableFuture.completedFuture(ClaimResult.CLAIMED);
                        } catch (RuntimeException exception) {
                            plugin.getLogger().severe("Failed to transfer shelf reward for player "
                                    + player.getUniqueId() + " and loot point " + lootPoint.id() + ": "
                                    + exception.getMessage());
                            return compensate(player, lootPoint, ClaimResult.PERSISTENCE_FAILURE);
                        }
                    });
        });
    }

    static Optional<ItemStack[]> plan(ItemStack[] contents, List<ItemStack> rewards) {
        ItemStack[] result = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) result[i] = copy(contents[i]);
        for (ItemStack reward : rewards) {
            int remaining = reward.getAmount();
            for (ItemStack stored : result) {
                if (stored != null && stored.isSimilar(reward) && stored.getAmount() < stored.getMaxStackSize()) {
                    int moved = Math.min(remaining, stored.getMaxStackSize() - stored.getAmount());
                    stored.setAmount(stored.getAmount() + moved);
                    remaining -= moved;
                    if (remaining == 0) break;
                }
            }
            for (int slot = 0; remaining > 0 && slot < result.length; slot++) {
                if (result[slot] == null || result[slot].getType() == Material.AIR) {
                    ItemStack added = reward.clone();
                    int moved = Math.min(remaining, added.getMaxStackSize());
                    added.setAmount(moved);
                    result[slot] = added;
                    remaining -= moved;
                }
            }
            if (remaining > 0) return Optional.empty();
        }
        return Optional.of(result);
    }

    private List<ItemStack> deserialize(LootPoint lootPoint) {
        return ShelfRewardTemplate.restore(persistenceService.shelfRewards(lootPoint.id()));
    }

    private CompletableFuture<ClaimResult> compensate(
            Player player,
            LootPoint lootPoint,
            ClaimResult result
    ) {
        return claimService.resetClaim(player.getUniqueId(), lootPoint.id())
                .thenApply(ignored -> result);
    }

    private <T> CompletableFuture<T> runOnMainThread(java.util.function.Supplier<T> supplier) {
        CompletableFuture<T> future = new CompletableFuture<>();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            try { future.complete(supplier.get()); }
            catch (RuntimeException exception) { future.completeExceptionally(exception); }
        });
        return future;
    }

    private static ItemStack copy(ItemStack item) {
        return item == null ? null : item.clone();
    }

    public enum ClaimResult {
        CLAIMED, ALREADY_CLAIMED, INSUFFICIENT_SPACE, MISSING_REWARD, PERSISTENCE_FAILURE
    }
}
