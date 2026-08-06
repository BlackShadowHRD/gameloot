package io.github.blackshadowhrd.poiloot.service;

import io.github.blackshadowhrd.poiloot.model.LootPoint;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.loot.LootTable;
import org.bukkit.loot.LootTables;

import java.util.List;
import java.util.Optional;

public final class LootGenerationService {

    private final Server server;

    public LootGenerationService(Server server) {
        this.server = server;
    }

    public Optional<LootTable> resolveLootTable(NamespacedKey key) {
        LootTables builtIn = Registry.LOOT_TABLES.get(key);
        if (builtIn != null) {
            return Optional.of(builtIn.getLootTable());
        }
        return Optional.ofNullable(server.getLootTable(key));
    }

    public List<ItemStack> generateLoot(LootPoint lootPoint, Player player, Location origin) {
        throw new UnsupportedOperationException("Private loot generation is not implemented yet");
    }
}
