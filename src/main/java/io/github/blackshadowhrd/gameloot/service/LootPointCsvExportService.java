package io.github.blackshadowhrd.gameloot.service;

import org.bukkit.plugin.Plugin;

import java.util.concurrent.CompletableFuture;

public final class LootPointCsvExportService {
    private final Plugin plugin;
    private final LootPointListingService listingService;
    private final LootPointCsvExporter exporter;

    public LootPointCsvExportService(
            Plugin plugin,
            LootPointListingService listingService,
            LootPointCsvExporter exporter
    ) {
        this.plugin = plugin;
        this.listingService = listingService;
        this.exporter = exporter;
    }

    public CompletableFuture<LootPointCsvExporter.ExportResult> export() {
        return listingService.list().thenCompose(entries -> {
            CompletableFuture<LootPointCsvExporter.ExportResult> future = new CompletableFuture<>();
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                try { future.complete(exporter.export(entries)); }
                catch (Exception exception) { future.completeExceptionally(exception); }
            });
            return future;
        });
    }
}
