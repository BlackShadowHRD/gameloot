package io.github.blackshadowhrd.gameloot.service;

import io.github.blackshadowhrd.gameloot.model.LootPointListEntry;
import io.github.blackshadowhrd.gameloot.model.LootPointType;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

public final class LootPointCsvExporter {
    public static final List<String> HEADER = List.of(
            "id", "target_type", "world", "world_uuid", "x", "y", "z", "entity_uuid",
            "loot_mode", "loot_table", "teleport_command");
    private static final DateTimeFormatter FILE_TIMESTAMP =
            DateTimeFormatter.ofPattern("uuuu-MM-dd-HHmmss").withZone(ZoneOffset.UTC);

    private final Path dataDirectory;
    private final Clock clock;

    public LootPointCsvExporter(Path dataDirectory) {
        this(dataDirectory, Clock.systemUTC());
    }

    LootPointCsvExporter(Path dataDirectory, Clock clock) {
        this.dataDirectory = dataDirectory.toAbsolutePath().normalize();
        this.clock = clock;
    }

    public synchronized ExportResult export(List<LootPointListEntry> entries) throws IOException {
        Path exports = dataDirectory.resolve("exports").normalize();
        if (!exports.startsWith(dataDirectory)) throw new IOException("Unsafe export directory");
        Files.createDirectories(exports);
        String base = "gameloot-lootpoints-" + FILE_TIMESTAMP.format(clock.instant());
        Path file = uniqueFile(exports, base);
        try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            CsvWriter.writeRow(writer, HEADER);
            for (LootPointListEntry entry : entries) CsvWriter.writeRow(writer, row(entry));
        }
        return new ExportResult(entries.size(), dataDirectory.relativize(file).toString().replace('\\', '/'));
    }

    private Path uniqueFile(Path exports, String base) {
        Path candidate = exports.resolve(base + ".csv");
        int suffix = 2;
        while (Files.exists(candidate)) candidate = exports.resolve(base + '-' + suffix++ + ".csv");
        return candidate;
    }

    private List<String> row(LootPointListEntry entry) {
        return List.of(
                entry.id().toString(),
                entry.persistedType().name(),
                entry.resolvedWorldName() == null ? "" : entry.resolvedWorldName(),
                entry.worldUuid().toString(),
                Integer.toString(entry.persistedX()), Integer.toString(entry.persistedY()),
                Integer.toString(entry.persistedZ()),
                entry.entityUuid() == null ? "" : entry.entityUuid().toString(),
                entry.persistedType() == LootPointType.SHELF ? "FIXED" : "LOOT_TABLE",
                entry.lootTable() == null ? "" : entry.lootTable().asString(),
                persistedTeleport(entry));
    }

    private String persistedTeleport(LootPointListEntry entry) {
        double x = entry.persistedX() + 0.5;
        double z = entry.persistedZ() + 0.5;
        int y = entry.persistedType() == LootPointType.CHEST_MINECART
                ? entry.persistedY() : entry.persistedY() + 1;
        return "/tp @s " + x + " " + y + " " + z;
    }

    public record ExportResult(int exported, String relativePath) { }
}
