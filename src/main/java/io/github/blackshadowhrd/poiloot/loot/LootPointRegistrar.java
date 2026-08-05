package io.github.blackshadowhrd.poiloot.loot;

import org.bukkit.NamespacedKey;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.TileState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.minecart.StorageMinecart;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

public final class LootPointRegistrar {

    private static final int TARGET_DISTANCE = 6;

    private final NamespacedKey idKey;
    private final NamespacedKey lootTableKey;

    public LootPointRegistrar(Plugin plugin) {
        idKey = new NamespacedKey(plugin, "loot_point_id");
        lootTableKey = new NamespacedKey(plugin, "loot_table");
    }

    public Result register(Player player, NamespacedKey lootTable) {
        Entity targetEntity = player.getTargetEntity(TARGET_DISTANCE);
        if (targetEntity instanceof StorageMinecart minecart) {
            return register(minecart.getPersistentDataContainer(), lootTable);
        }

        Block targetBlock = player.getTargetBlockExact(TARGET_DISTANCE);
        BlockState targetState = targetBlock == null ? null : targetBlock.getState();
        if (!(targetState instanceof Chest || targetState instanceof Barrel)) {
            return Result.INVALID_TARGET;
        }

        TileState tileState = (TileState) targetState;
        Result result = register(tileState.getPersistentDataContainer(), lootTable);
        if (result == Result.REGISTERED) {
            tileState.update();
        }
        return result;
    }

    private Result register(PersistentDataContainer data, NamespacedKey lootTable) {
        if (data.has(idKey)) {
            return Result.ALREADY_REGISTERED;
        }

        data.set(idKey, PersistentDataType.STRING, UUID.randomUUID().toString());
        data.set(lootTableKey, PersistentDataType.STRING, lootTable.asString());
        return Result.REGISTERED;
    }

    public enum Result {
        REGISTERED,
        ALREADY_REGISTERED,
        INVALID_TARGET
    }
}
