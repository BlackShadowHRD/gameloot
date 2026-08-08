package io.github.blackshadowhrd.gameloot.service;

import io.github.blackshadowhrd.gameloot.target.LootPointResolution;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.BlockInventoryHolder;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class LootPointProtectionService {

    private final LootPointLookupService lookupService;

    public LootPointProtectionService(LootPointLookupService lookupService) {
        this.lookupService = lookupService;
    }

    public LootPointResolution resolve(Block block) {
        return lookupService.resolveTarget(block);
    }

    public LootPointResolution resolve(Entity entity) {
        return lookupService.resolveTarget(entity);
    }

    public boolean isProtected(Block block) {
        return lookupService.hasRegistrationMarker(block);
    }

    public boolean isProtectedAccess(Block block) {
        if (isProtected(block)) return true;
        return block != null
                && block.getState() instanceof InventoryHolder holder
                && isProtected(holder.getInventory());
    }

    public boolean isProtected(Entity entity) {
        return lookupService.hasRegistrationMarker(entity);
    }

    public boolean isProtected(Inventory inventory) {
        return isProtected(inventory.getHolder(false));
    }

    private boolean isProtected(InventoryHolder holder) {
        if (holder instanceof DoubleChest doubleChest) {
            return isProtected(doubleChest.getLeftSide(false)) || isProtected(doubleChest.getRightSide(false));
        }
        if (holder instanceof BlockInventoryHolder blockHolder) {
            return isProtected(blockHolder.getBlock());
        }
        if (holder instanceof BlockState state) {
            return isProtected(state.getBlock());
        }
        return holder instanceof Entity entity && isProtected(entity);
    }
}
