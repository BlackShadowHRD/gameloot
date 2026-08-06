package io.github.blackshadowhrd.poiloot.service;

import io.github.blackshadowhrd.poiloot.model.LootPoint;
import io.github.blackshadowhrd.poiloot.model.LootPointType;
import io.github.blackshadowhrd.poiloot.target.BlockLootPointTarget;
import io.github.blackshadowhrd.poiloot.target.EntityLootPointTarget;
import io.github.blackshadowhrd.poiloot.target.LootPointTarget;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.minecart.StorageMinecart;
import org.bukkit.persistence.PersistentDataContainer;

sealed interface MutableLootPointTarget permits MutableBlockLootPointTarget, MutableEntityLootPointTarget {

    PersistentDataContainer data();

    LootPointType type();

    String displayType();

    Location location();

    String description();

    LootPointTarget withLootPoint(LootPoint lootPoint);

    boolean persist();
}

record MutableBlockLootPointTarget(Block block, Container state, String displayType)
        implements MutableLootPointTarget {

    @Override
    public PersistentDataContainer data() {
        return state.getPersistentDataContainer();
    }

    @Override
    public LootPointType type() {
        return LootPointType.BLOCK_CONTAINER;
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

record MutableEntityLootPointTarget(StorageMinecart entity) implements MutableLootPointTarget {

    @Override
    public PersistentDataContainer data() {
        return entity.getPersistentDataContainer();
    }

    @Override
    public LootPointType type() {
        return LootPointType.CHEST_MINECART;
    }

    @Override
    public String displayType() {
        return "Chest Minecart";
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
