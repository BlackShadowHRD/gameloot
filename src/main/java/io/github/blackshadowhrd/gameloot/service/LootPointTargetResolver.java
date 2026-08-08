package io.github.blackshadowhrd.gameloot.service;

import io.github.blackshadowhrd.gameloot.model.LootPointTargetType;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.Shelf;
import org.bukkit.entity.Entity;
import org.bukkit.entity.minecart.StorageMinecart;

import java.util.Optional;

public final class LootPointTargetResolver {

    public Optional<MutableLootPointTarget> resolve(Block block) {
        if (block == null) return Optional.empty();
        Optional<LootPointTargetType> type = LootPointTargetType.fromMaterial(block.getType());
        if (type.isEmpty()) return Optional.empty();
        BlockState state = block.getState();
        if ((type.get() == LootPointTargetType.SHELF && state instanceof Shelf)
                || (type.get().lootMode() == LootPointTargetType.LootMode.LOOT_TABLE
                && state instanceof Container)) {
            return Optional.of(new MutableBlockLootPointTarget(block, (org.bukkit.block.TileState) state, type.get()));
        }
        return Optional.empty();
    }

    public Optional<MutableLootPointTarget> resolve(Entity entity) {
        if (entity == null) return Optional.empty();
        Optional<LootPointTargetType> type = LootPointTargetType.fromEntityType(entity.getType());
        if (type.isPresent() && entity instanceof StorageMinecart minecart) {
            return Optional.of(new MutableEntityLootPointTarget(minecart, type.get()));
        }
        return Optional.empty();
    }
}
