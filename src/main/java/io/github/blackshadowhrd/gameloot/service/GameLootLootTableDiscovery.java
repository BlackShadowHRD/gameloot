package io.github.blackshadowhrd.gameloot.service;

import org.bukkit.NamespacedKey;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class GameLootLootTableDiscovery {
    static final String RESOURCE_PREFIX = "data/gameloot/loot_table/";

    public Set<NamespacedKey> discover(Path datapacksDirectory, Set<String> enabledPackNames)
            throws IOException {
        Set<NamespacedKey> keys = new HashSet<>();
        if (!Files.isDirectory(datapacksDirectory)) return Set.of();
        try (Stream<Path> entries = Files.list(datapacksDirectory)) {
            for (Path pack : entries.toList()) {
                if (Files.isSymbolicLink(pack) || !isEnabled(pack, enabledPackNames)) continue;
                if (Files.isDirectory(pack)) discoverDirectory(pack, keys::add);
                else if (pack.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".zip")) {
                    discoverZip(pack, keys::add);
                }
            }
        }
        return Set.copyOf(keys);
    }

    static NamespacedKey resourceKey(String resourcePath) {
        String normalized = resourcePath.replace('\\', '/');
        if (!normalized.startsWith(RESOURCE_PREFIX) || !normalized.endsWith(".json")
                || normalized.contains("../") || normalized.startsWith("/")) return null;
        String value = normalized.substring(RESOURCE_PREFIX.length(), normalized.length() - 5);
        if (value.isBlank()) return null;
        try {
            return NamespacedKey.fromString("gameloot:" + value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private void discoverDirectory(Path pack, Consumer<NamespacedKey> output) throws IOException {
        Path root = pack.resolve(RESOURCE_PREFIX).normalize();
        if (!root.startsWith(pack.normalize()) || !Files.isDirectory(root)) return;
        try (Stream<Path> resources = Files.walk(root)) {
            resources.filter(Files::isRegularFile)
                    .filter(path -> !Files.isSymbolicLink(path))
                    .map(path -> resourceKey(pack.relativize(path).toString()))
                    .filter(java.util.Objects::nonNull)
                    .forEach(output);
        }
    }

    private void discoverZip(Path pack, Consumer<NamespacedKey> output) throws IOException {
        try (ZipFile zip = new ZipFile(pack.toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) continue;
                NamespacedKey key = resourceKey(entry.getName());
                if (key != null) output.accept(key);
            }
        }
    }

    private boolean isEnabled(Path pack, Set<String> enabledPackNames) {
        String name = pack.getFileName().toString();
        return enabledPackNames.contains(name) || enabledPackNames.contains("file/" + name);
    }
}
