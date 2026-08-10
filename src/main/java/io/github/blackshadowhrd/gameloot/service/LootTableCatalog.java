package io.github.blackshadowhrd.gameloot.service;

import io.papermc.paper.datapack.DatapackSource;
import io.papermc.paper.event.server.ServerResourcesReloadedEvent;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Server;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;

public final class LootTableCatalog implements Listener {
    private final Plugin plugin;
    private final Server server;
    private final GameLootLootTableDiscovery discovery;
    private final AtomicReference<List<String>> keys = new AtomicReference<>(List.of());
    private final AtomicLong refreshGeneration = new AtomicLong();

    public LootTableCatalog(Plugin plugin, GameLootLootTableDiscovery discovery) {
        this.plugin = plugin;
        server = plugin.getServer();
        this.discovery = discovery;
    }

    public void refresh() {
        long generation = refreshGeneration.incrementAndGet();
        Set<NamespacedKey> vanilla = Set.copyOf(Registry.LOOT_TABLES.keyStream().toList());
        keys.set(merge(vanilla, List.of()));
        Set<String> enabledWorldPacks = new HashSet<>();
        server.getDatapackManager().getEnabledPacks().stream()
                .filter(pack -> pack.getSource().equals(DatapackSource.WORLD))
                .map(pack -> pack.getName())
                .forEach(enabledWorldPacks::add);
        var datapacksDirectory = server.getLevelDirectory().resolve("datapacks");

        server.getScheduler().runTaskAsynchronously(plugin, () -> {
            Set<NamespacedKey> discovered = Set.of();
            try {
                discovered = discovery.discover(datapacksDirectory, enabledWorldPacks);
            } catch (IOException | RuntimeException exception) {
                plugin.getLogger().log(Level.WARNING,
                        "Unable to discover GameLoot datapack loot tables; vanilla suggestions remain available",
                        exception);
            }
            Set<NamespacedKey> candidates = discovered;
            server.getScheduler().runTask(plugin, () -> {
                if (refreshGeneration.get() == generation) replace(vanilla, candidates);
            });
        });
    }

    public List<String> suggestions(String prefix) {
        return filter(keys.get(), prefix);
    }

    @EventHandler
    public void onResourcesReloaded(ServerResourcesReloadedEvent event) {
        refresh();
    }

    private void replace(Set<NamespacedKey> vanilla, Set<NamespacedKey> discovered) {
        Set<NamespacedKey> loadedCustom = loadedCustom(discovered, key -> server.getLootTable(key) != null);
        keys.set(merge(vanilla, loadedCustom));
    }

    static Set<NamespacedKey> loadedCustom(
            Collection<NamespacedKey> discovered,
            java.util.function.Predicate<NamespacedKey> resolves
    ) {
        Set<NamespacedKey> loaded = new HashSet<>();
        discovered.stream().filter(key -> key.namespace().equals("gameloot"))
                .filter(resolves).forEach(loaded::add);
        return Set.copyOf(loaded);
    }

    static List<String> merge(Collection<NamespacedKey> vanilla, Collection<NamespacedKey> custom) {
        Set<String> merged = new HashSet<>();
        vanilla.stream().map(NamespacedKey::asString).forEach(merged::add);
        custom.stream().filter(key -> key.namespace().equals("gameloot"))
                .map(NamespacedKey::asString).forEach(merged::add);
        return merged.stream().sorted().toList();
    }

    static List<String> filter(Collection<String> keys, String prefix) {
        String normalized = prefix.toLowerCase(Locale.ROOT);
        return keys.stream().filter(key -> key.toLowerCase(Locale.ROOT).startsWith(normalized))
                .sorted().toList();
    }
}
