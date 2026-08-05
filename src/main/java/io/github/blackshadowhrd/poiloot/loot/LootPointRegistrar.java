package io.github.blackshadowhrd.poiloot.loot;

import org.bukkit.NamespacedKey;
import org.bukkit.Location;
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
import java.util.Optional;

public final class LootPointRegistrar {

    private static final int TARGET_DISTANCE = 6;

    private final NamespacedKey idKey;
    private final NamespacedKey lootTableKey;

    public LootPointRegistrar(Plugin plugin) {
        idKey = new NamespacedKey(plugin, "loot_point_id");
        lootTableKey = new NamespacedKey(plugin, "loot_table");
    }

    public Result register(Player player, NamespacedKey lootTable) {
        Target target = findTarget(player);
        if (target == null) {
            return Result.INVALID_TARGET;
        }

        Result result = register(target.data(), lootTable);
        if (result == Result.REGISTERED && target.state() != null) {
            target.state().update();
        }
        return result;
    }

    public Optional<Inspection> inspect(Player player) {
        Target target = findTarget(player);
        if (target == null) {
            return Optional.empty();
        }

        String id = target.data().get(idKey, PersistentDataType.STRING);
        String lootTable = target.data().get(lootTableKey, PersistentDataType.STRING);
        Location location = target.location();
        return Optional.of(new Inspection(
                id != null,
                id,
                target.type(),
                lootTable,
                location.getWorld().getName() + " (" + location.getBlockX() + ", "
                        + location.getBlockY() + ", " + location.getBlockZ() + ")"
        ));
    }

    private Target findTarget(Player player) {
        Entity targetEntity = player.getTargetEntity(TARGET_DISTANCE);
        if (targetEntity instanceof StorageMinecart minecart) {
            return new Target(minecart.getPersistentDataContainer(), null, "Chest Minecart", minecart.getLocation());
        }

        Block targetBlock = player.getTargetBlockExact(TARGET_DISTANCE);
        BlockState targetState = targetBlock == null ? null : targetBlock.getState();
        if (targetState instanceof Chest chest) {
            return new Target(chest.getPersistentDataContainer(), chest, "Chest", chest.getLocation());
        }
        if (targetState instanceof Barrel barrel) {
            return new Target(barrel.getPersistentDataContainer(), barrel, "Barrel", barrel.getLocation());
        }
        return null;
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

    public record Inspection(boolean registered, String id, String type, String lootTable, String location) {
    }

    private record Target(PersistentDataContainer data, TileState state, String type, Location location) {
    }
}
