package io.github.blackshadowhrd.gameloot.service;

import io.github.blackshadowhrd.gameloot.model.LootPoint;
import io.github.blackshadowhrd.gameloot.model.LootPointType;
import io.github.blackshadowhrd.gameloot.model.LootPointTargetType;
import io.github.blackshadowhrd.gameloot.target.BlockLootPointTarget;
import io.github.blackshadowhrd.gameloot.target.EntityLootPointTarget;
import io.github.blackshadowhrd.gameloot.target.LootPointTarget;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.entity.minecart.StorageMinecart;
import org.bukkit.persistence.PersistentDataContainer;

sealed interface MutableLootPointTarget permits MutableBlockLootPointTarget, MutableEntityLootPointTarget {

    PersistentDataContainer data();

    LootPointTargetType targetType();

    default LootPointType type() { return targetType().persistedType(); }

    String displayType();

    Location location();

    String description();

    LootPointTarget withLootPoint(LootPoint lootPoint);

    boolean persist();
}

record MutableBlockLootPointTarget(Block block, TileState state, LootPointTargetType targetType)
        implements MutableLootPointTarget {

    @Override
    public PersistentDataContainer data() {
        return state.getPersistentDataContainer();
    }

    @Override
    public String displayType() {
        return targetType.displayName();
    }

    @Override
    public Location location() {
        return block.getLocation();
    }

    @Override
    public String description() {
        Location location = location();
        return block.getType() + " at " + location.getWorld().getName() + " ("
                + location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ() + ")";
    }

    @Override
    public LootPointTarget withLootPoint(LootPoint lootPoint) {
        return new BlockLootPointTarget(lootPoint, block);
    }

    @Override
    public boolean persist() {
        return state.update();
    }
}

record MutableEntityLootPointTarget(StorageMinecart entity, LootPointTargetType targetType)
        implements MutableLootPointTarget {

    @Override
    public PersistentDataContainer data() {
        return entity.getPersistentDataContainer();
    }

    @Override
    public String displayType() {
        return targetType.displayName();
    }

    @Override
    public Location location() {
        return entity.getLocation();
    }

    @Override
    public String description() {
        Location location = location();
        return "chest minecart " + entity.getUniqueId() + " at " + location.getWorld().getName() + " ("
                + location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ() + ")";
    }

    @Override
    public LootPointTarget withLootPoint(LootPoint lootPoint) {
        return new EntityLootPointTarget(lootPoint, entity);
    }

    @Override
    public boolean persist() {
        // Entity PDC changes are applied directly to the live entity.
        return true;
    }
}
