package io.github.blackshadowhrd.poiloot.service;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

public final class LootPointRegistrar {

    private static final double TARGET_DISTANCE = 6;

    private final LootPointLookupService lookupService;
    private final NamespacedKey idKey;
    private final NamespacedKey lootTableKey;

    public LootPointRegistrar(Plugin plugin, LootPointLookupService lookupService) {
        this.lookupService = lookupService;
        idKey = new NamespacedKey(plugin, "loot_point_id");
        lootTableKey = new NamespacedKey(plugin, "loot_table");
    }

    public Result register(Player player, NamespacedKey lootTable) {
        return lookupService.findSupportedTarget(player, TARGET_DISTANCE)
                .map(target -> register(target, lootTable))
                .orElse(Result.INVALID_TARGET);
    }

    public DeregisterResult deregister(Player player) {
        return lookupService.findSupportedTarget(player, TARGET_DISTANCE)
                .map(this::deregister)
                .orElse(DeregisterResult.INVALID_TARGET);
    }

    private Result register(MutableLootPointTarget target, NamespacedKey lootTable) {
        PersistentDataContainer data = target.data();
        if (data.has(idKey)) {
            return Result.ALREADY_REGISTERED;
        }

        data.set(idKey, PersistentDataType.STRING, UUID.randomUUID().toString());
        data.set(lootTableKey, PersistentDataType.STRING, lootTable.asString());
        target.persist();
        return Result.REGISTERED;
    }

    private DeregisterResult deregister(MutableLootPointTarget target) {
        PersistentDataContainer data = target.data();
        if (!data.has(idKey) && !data.has(lootTableKey)) {
            return DeregisterResult.NOT_REGISTERED;
        }

        data.remove(idKey);
        data.remove(lootTableKey);
        target.persist();
        return DeregisterResult.DEREGISTERED;
    }

    public enum Result {
        REGISTERED,
        ALREADY_REGISTERED,
        INVALID_TARGET
    }

    public enum DeregisterResult {
        DEREGISTERED,
        NOT_REGISTERED,
        INVALID_TARGET
    }
}
