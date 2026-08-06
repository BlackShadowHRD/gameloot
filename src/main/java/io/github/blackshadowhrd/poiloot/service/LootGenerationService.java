package io.github.blackshadowhrd.poiloot.service;

import io.github.blackshadowhrd.poiloot.model.LootPoint;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.loot.LootContext;
import org.bukkit.loot.LootTable;
import org.bukkit.loot.LootTables;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;

public final class LootGenerationService {

    private final Server server;

    public LootGenerationService(Server server) {
        this.server = server;
    }

    public Optional<LootTable> resolveLootTable(NamespacedKey key) {
        LootTables builtIn = Registry.LOOT_TABLES.get(key);
        if (builtIn != null) {
            return Optional.ofNullable(builtIn.getLootTable());
        }
        return Optional.ofNullable(server.getLootTable(key));
    }

    public List<ItemStack> generateLoot(LootPoint lootPoint, Player player, Location origin) {
        Objects.requireNonNull(lootPoint, "lootPoint");
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(origin, "origin");

        LootTable lootTable = resolveLootTable(lootPoint.lootTable())
                .orElseThrow(() -> new IllegalArgumentException("Unknown loot table: " + lootPoint.lootTable()));
        LootContext context = new LootContext.Builder(origin)
                .killer(player)
                .build();
        return List.copyOf(new ArrayList<>(lootTable.populateLoot(new Random(), context)));
    }
}
