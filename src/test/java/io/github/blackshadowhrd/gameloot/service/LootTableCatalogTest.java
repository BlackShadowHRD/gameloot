package io.github.blackshadowhrd.gameloot.service;

import org.bukkit.NamespacedKey;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LootTableCatalogTest {
    @Test
    void mergesVanillaAndOnlyGameLootCustomKeysWithoutDuplicates() {
        assertEquals(List.of("gameloot:mining_camp/common", "minecraft:chests/simple_dungeon"),
                LootTableCatalog.merge(
                        Set.of(NamespacedKey.minecraft("chests/simple_dungeon")),
                        List.of(NamespacedKey.fromString("gameloot:mining_camp/common"),
                                NamespacedKey.minecraft("chests/simple_dungeon"),
                                NamespacedKey.fromString("other:ignored"))));
    }

    @Test
    void filtersCaseSafelyByRemainingPrefix() {
        List<String> keys = List.of("minecraft:chests/a", "gameloot:mining_camp/rare",
                "gameloot:mining_camp/common");
        assertEquals(List.of("gameloot:mining_camp/common", "gameloot:mining_camp/rare"),
                LootTableCatalog.filter(keys, "GAMELOOT:mining_camp/"));
    }

    @Test
    void includesOnlyResolvableGameLootTables() {
        NamespacedKey loaded = NamespacedKey.fromString("gameloot:loaded");
        NamespacedKey missing = NamespacedKey.fromString("gameloot:missing");
        assertEquals(Set.of(loaded), LootTableCatalog.loadedCustom(
                List.of(loaded, missing, NamespacedKey.fromString("other:loaded")),
                loaded::equals));
    }

    @Test
    void rebuildOutputReplacesRatherThanAccumulatesStaleCustomKeys() {
        var vanilla = Set.of(NamespacedKey.minecraft("chests/simple_dungeon"));
        List<String> first = LootTableCatalog.merge(vanilla,
                Set.of(NamespacedKey.fromString("gameloot:old")));
        List<String> refreshed = LootTableCatalog.merge(vanilla,
                Set.of(NamespacedKey.fromString("gameloot:new")));

        assertEquals(List.of("gameloot:old", "minecraft:chests/simple_dungeon"), first);
        assertEquals(List.of("gameloot:new", "minecraft:chests/simple_dungeon"), refreshed);
    }
}
