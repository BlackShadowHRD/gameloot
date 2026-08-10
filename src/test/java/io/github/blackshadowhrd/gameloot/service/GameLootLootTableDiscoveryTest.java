package io.github.blackshadowhrd.gameloot.service;

import org.bukkit.NamespacedKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class GameLootLootTableDiscoveryTest {
    @TempDir Path temporaryDirectory;

    @Test
    void convertsNestedGameLootResourcesAndRejectsMalformedPaths() {
        assertEquals(NamespacedKey.fromString("gameloot:mining_camp/common"),
                GameLootLootTableDiscovery.resourceKey(
                        "data/gameloot/loot_table/mining_camp/common.json"));
        assertEquals(NamespacedKey.fromString("gameloot:crash_site/deep/rare"),
                GameLootLootTableDiscovery.resourceKey(
                        "data/gameloot/loot_table/crash_site/deep/rare.json"));
        assertNull(GameLootLootTableDiscovery.resourceKey("data/other/loot_table/common.json"));
        assertNull(GameLootLootTableDiscovery.resourceKey("data/gameloot/loot_table/common.txt"));
        assertNull(GameLootLootTableDiscovery.resourceKey("data/gameloot/loot_table/../secret.json"));
    }

    @Test
    void discoversOnlyEnabledDirectoryDatapacks() throws IOException {
        Path enabled = temporaryDirectory.resolve("enabled-pack");
        Path disabled = temporaryDirectory.resolve("disabled-pack");
        write(enabled, "data/gameloot/loot_table/mining_camp/common.json");
        write(enabled, "data/other/loot_table/ignored.json");
        write(enabled, "data/gameloot/loot_table/ignored.txt");
        write(disabled, "data/gameloot/loot_table/mining_camp/rare.json");

        Set<NamespacedKey> keys = new GameLootLootTableDiscovery().discover(
                temporaryDirectory, Set.of("file/enabled-pack"));

        assertEquals(Set.of(NamespacedKey.fromString("gameloot:mining_camp/common")), keys);
    }

    @Test
    void discoversEnabledZipDatapacksAndRemovesDuplicates() throws IOException {
        Path zip = temporaryDirectory.resolve("gameloot.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip))) {
            add(output, "data/gameloot/loot_table/mining_camp/common.json");
            add(output, "data/gameloot/loot_table/mining_camp/common.json", true);
            add(output, "data/unrelated/loot_table/nope.json");
        }

        Set<NamespacedKey> keys = new GameLootLootTableDiscovery().discover(
                temporaryDirectory, Set.of("gameloot.zip"));
        assertEquals(Set.of(NamespacedKey.fromString("gameloot:mining_camp/common")), keys);
    }

    private void write(Path pack, String resource) throws IOException {
        Path file = pack.resolve(resource);
        Files.createDirectories(file.getParent());
        Files.writeString(file, "{}");
    }

    private void add(ZipOutputStream output, String name) throws IOException {
        add(output, name, false);
    }

    private void add(ZipOutputStream output, String name, boolean duplicate) throws IOException {
        if (duplicate) return;
        output.putNextEntry(new ZipEntry(name));
        output.write("{}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        output.closeEntry();
    }
}
