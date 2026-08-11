package io.github.blackshadowhrd.gameloot.service;

import io.github.blackshadowhrd.gameloot.model.LootPointListEntry;
import io.github.blackshadowhrd.gameloot.model.LootPointType;
import org.bukkit.NamespacedKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LootPointCsvExporterTest {
    @TempDir Path temporaryDirectory;
    private final Clock clock = Clock.fixed(Instant.parse("2026-08-11T07:15:30Z"), ZoneOffset.UTC);

    @Test
    void exportsHeaderAndAllTargetModesInInputOrder() throws Exception {
        UUID worldId = UUID.randomUUID();
        UUID entityId = UUID.randomUUID();
        var standard = entry(UUID.randomUUID(), LootPointType.BLOCK_CONTAINER, "world", worldId,
                10, 64, -20, null, NamespacedKey.fromString("gameloot:mining_camp/common"));
        var shelf = entry(UUID.randomUUID(), LootPointType.SHELF, null, worldId,
                11, 65, -21, null, null);
        var minecart = entry(UUID.randomUUID(), LootPointType.CHEST_MINECART, "mine,\"cart\"\nworld", worldId,
                12, 66, -22, entityId, NamespacedKey.minecraft("chests/abandoned_mineshaft"));

        var result = new LootPointCsvExporter(temporaryDirectory, clock).export(List.of(standard, shelf, minecart));
        String csv = Files.readString(temporaryDirectory.resolve(result.relativePath()));

        assertEquals(3, result.exported());
        assertTrue(csv.startsWith(String.join(",", LootPointCsvExporter.HEADER) + "\r\n"));
        assertTrue(csv.indexOf(standard.id().toString()) < csv.indexOf(shelf.id().toString()));
        assertTrue(csv.indexOf(shelf.id().toString()) < csv.indexOf(minecart.id().toString()));
        assertTrue(csv.contains(",LOOT_TABLE,gameloot:mining_camp/common,/tp @s 10.5 65 -19.5"));
        assertTrue(csv.contains(",FIXED,,/tp @s 11.5 66 -20.5"));
        assertTrue(csv.contains(entityId + ",LOOT_TABLE,minecraft:chests/abandoned_mineshaft,"));
        assertTrue(csv.contains("\"mine,\"\"cart\"\"\nworld\""));
        assertTrue(csv.contains("," + worldId + ",11,65,-21,"));
    }

    @Test
    void exportsHeaderForEmptyDatabaseAndUsesCollisionSuffix() throws Exception {
        LootPointCsvExporter exporter = new LootPointCsvExporter(temporaryDirectory, clock);
        var first = exporter.export(List.of());
        var second = exporter.export(List.of());

        assertEquals("exports/gameloot-lootpoints-2026-08-11-071530.csv", first.relativePath());
        assertEquals("exports/gameloot-lootpoints-2026-08-11-071530-2.csv", second.relativePath());
        assertNotEquals(first.relativePath(), second.relativePath());
        assertEquals(String.join(",", LootPointCsvExporter.HEADER) + "\r\n",
                Files.readString(temporaryDirectory.resolve(first.relativePath())));
    }

    @Test
    void escapesCommasQuotesAndLineBreaks() {
        assertEquals("plain", CsvWriter.escape("plain"));
        assertEquals("\"a,b\"", CsvWriter.escape("a,b"));
        assertEquals("\"a\"\"b\"", CsvWriter.escape("a\"b"));
        assertEquals("\"a\nb\"", CsvWriter.escape("a\nb"));
    }

    private LootPointListEntry entry(
            UUID id,
            LootPointType type,
            String resolvedWorld,
            UUID worldId,
            int x,
            int y,
            int z,
            UUID entityId,
            NamespacedKey lootTable
    ) {
        String worldDisplay = resolvedWorld == null ? worldId + " (unavailable)" : resolvedWorld;
        return new LootPointListEntry(id, type, type.name(), worldDisplay, resolvedWorld, worldId,
                x, y, z, x, y, z, entityId, lootTable, type == LootPointType.SHELF, null,
                type == LootPointType.CHEST_MINECART);
    }
}
