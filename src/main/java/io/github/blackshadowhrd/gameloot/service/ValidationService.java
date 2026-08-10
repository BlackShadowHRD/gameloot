package io.github.blackshadowhrd.gameloot.service;

import io.github.blackshadowhrd.gameloot.model.LootPointType;
import io.github.blackshadowhrd.gameloot.repository.ValidationRepository;
import io.github.blackshadowhrd.gameloot.repository.model.LootPointRecord;
import io.github.blackshadowhrd.gameloot.repository.model.ShelfRewardItem;
import io.github.blackshadowhrd.gameloot.validation.DatabaseIntegrityReport;
import io.github.blackshadowhrd.gameloot.validation.LootPointValidationResult;
import io.github.blackshadowhrd.gameloot.validation.ValidationDecisions;
import io.github.blackshadowhrd.gameloot.validation.ValidationIssue;
import io.github.blackshadowhrd.gameloot.validation.ValidationReport;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class ValidationService {
    private static final int RECORDS_PER_TICK = 50;

    private final Plugin plugin;
    private final Server server;
    private final LootPointPersistenceService persistenceService;
    private final ValidationRepository validationRepository;
    private final LootPointLookupService lookupService;
    private final LootPointTargetResolver targetResolver;
    private final LootGenerationService generationService;

    public ValidationService(
            Plugin plugin,
            LootPointPersistenceService persistenceService,
            ValidationRepository validationRepository,
            LootPointLookupService lookupService,
            LootPointTargetResolver targetResolver,
            LootGenerationService generationService
    ) {
        this.plugin = plugin;
        server = plugin.getServer();
        this.persistenceService = persistenceService;
        this.validationRepository = validationRepository;
        this.lookupService = lookupService;
        this.targetResolver = targetResolver;
        this.generationService = generationService;
    }

    public CompletableFuture<ValidationReport> validate() {
        List<LootPointRecord> records = persistenceService.records();
        Set<UUID> duplicates = duplicatePhysicalTargets(records);
        return validationRepository.validateIntegrity().thenCompose(database ->
                validateOnMainThread(records, duplicates, database));
    }

    private CompletableFuture<ValidationReport> validateOnMainThread(
            List<LootPointRecord> records,
            Set<UUID> duplicates,
            DatabaseIntegrityReport database
    ) {
        CompletableFuture<ValidationReport> future = new CompletableFuture<>();
        List<LootPointValidationResult> results = new ArrayList<>(records.size());
        class Batch implements Runnable {
            private int index;

            @Override
            public void run() {
                try {
                    int end = Math.min(index + RECORDS_PER_TICK, records.size());
                    while (index < end) {
                        LootPointRecord record = records.get(index++);
                        results.add(validateRecord(record, duplicates.contains(record.id())));
                    }
                    if (index < records.size()) {
                        server.getScheduler().runTask(plugin, this);
                    } else {
                        future.complete(new ValidationReport(results, database));
                    }
                } catch (RuntimeException exception) {
                    future.completeExceptionally(exception);
                }
            }
        }
        server.getScheduler().runTask(plugin, new Batch());
        return future;
    }

    private LootPointValidationResult validateRecord(LootPointRecord record, boolean duplicate) {
        List<ValidationIssue> issues = new ArrayList<>();
        if (duplicate) issues.add(ValidationIssue.DUPLICATE_PHYSICAL_TARGET);
        validateMetadata(record, issues);
        validateRewardOrLootTable(record, issues);

        World world = server.getWorld(record.worldUuid());
        if (world == null) {
            issues.add(ValidationIssue.WORLD_UNAVAILABLE);
            return new LootPointValidationResult(record, issues);
        }
        if (record.targetType() == LootPointType.CHEST_MINECART) {
            validateEntity(record, world, issues);
        } else {
            validateBlock(record, world, issues);
        }
        return new LootPointValidationResult(record, issues);
    }

    private void validateMetadata(LootPointRecord record, List<ValidationIssue> issues) {
        boolean shelfMetadata = record.targetType() == LootPointType.SHELF && record.lootTable() == null;
        boolean tableMetadata = record.targetType() != LootPointType.SHELF && record.lootTable() != null;
        if (!shelfMetadata && !tableMetadata) issues.add(ValidationIssue.INVALID_LOOT_METADATA);
        if (record.targetType() == LootPointType.CHEST_MINECART && record.entityUuid() == null) {
            issues.add(ValidationIssue.ENTITY_UUID_MISSING);
        } else if (record.targetType() != LootPointType.CHEST_MINECART && record.entityUuid() != null) {
            issues.add(ValidationIssue.INVALID_LOOT_METADATA);
        }
    }

    private void validateRewardOrLootTable(LootPointRecord record, List<ValidationIssue> issues) {
        if (record.targetType() == LootPointType.SHELF) {
            List<ShelfRewardItem> rewards = persistenceService.shelfRewards(record.id());
            boolean valid = false;
            if (!rewards.isEmpty()) {
                try {
                    List<ItemStack> items = ShelfRewardTemplate.restore(rewards);
                    valid = !items.isEmpty() && items.stream().noneMatch(ItemStack::isEmpty);
                } catch (RuntimeException exception) {
                    valid = false;
                }
            }
            ValidationDecisions.shelfReward(!rewards.isEmpty(), valid).ifPresent(issues::add);
        } else if (record.lootTable() != null) {
            ValidationDecisions.lootTable(generationService.resolveLootTable(record.lootTable()).isPresent())
                    .ifPresent(issues::add);
        }
    }

    private void validateBlock(LootPointRecord record, World world, List<ValidationIssue> issues) {
        int chunkX = Math.floorDiv(record.x(), 16);
        int chunkZ = Math.floorDiv(record.z(), 16);
        if (!world.isChunkLoaded(chunkX, chunkZ)) {
            issues.add(ValidationIssue.CHUNK_NOT_LOADED);
            return;
        }
        var block = world.getBlockAt(record.x(), record.y(), record.z());
        Optional<MutableLootPointTarget> target = targetResolver.resolve(block);
        if (target.isEmpty()) {
            issues.add(block.getType().isAir()
                    ? ValidationIssue.TARGET_MISSING
                    : ValidationIssue.TARGET_TYPE_MISMATCH);
            return;
        }
        if (target.get().type() != record.targetType()) {
            issues.add(ValidationIssue.TARGET_TYPE_MISMATCH);
            return;
        }
        validateMarker(record, target.get(), issues);
    }

    private void validateEntity(LootPointRecord record, World world, List<ValidationIssue> issues) {
        if (record.entityUuid() == null) return;
        Entity entity = world.getEntity(record.entityUuid());
        boolean expectedChunkLoaded = world.isChunkLoaded(
                Math.floorDiv(record.x(), 16), Math.floorDiv(record.z(), 16));
        Optional<ValidationIssue> availability = ValidationDecisions.entity(entity != null, expectedChunkLoaded);
        if (availability.isPresent()) {
            issues.add(availability.get());
            return;
        }
        Optional<MutableLootPointTarget> target = targetResolver.resolve(entity);
        if (target.isEmpty() || target.get().type() != LootPointType.CHEST_MINECART) {
            issues.add(ValidationIssue.TARGET_TYPE_MISMATCH);
            return;
        }
        Location location = entity.getLocation();
        if (location.getBlockX() != record.x() || location.getBlockY() != record.y()
                || location.getBlockZ() != record.z()) {
            issues.add(ValidationIssue.ENTITY_LOCATION_MISMATCH);
        }
        validateMarker(record, target.get(), issues);
    }

    private void validateMarker(
            LootPointRecord record,
            MutableLootPointTarget target,
            List<ValidationIssue> issues
    ) {
        LootPointLookupService.MarkerInspection marker = lookupService.inspectMarker(target);
        ValidationDecisions.pdc(
                marker.present(),
                marker.id().isPresent(),
                marker.id().filter(record.id()::equals).isPresent()
        ).ifPresent(issues::add);
    }

    private Set<UUID> duplicatePhysicalTargets(List<LootPointRecord> records) {
        Map<String, UUID> firstByTarget = new HashMap<>();
        Set<UUID> duplicates = new HashSet<>();
        for (LootPointRecord record : records) {
            String key = record.targetType() == LootPointType.CHEST_MINECART && record.entityUuid() != null
                    ? "entity:" + record.worldUuid() + ':' + record.entityUuid()
                    : "block:" + record.worldUuid() + ':' + record.x() + ':' + record.y() + ':' + record.z();
            UUID first = firstByTarget.putIfAbsent(key, record.id());
            if (first != null) {
                duplicates.add(first);
                duplicates.add(record.id());
            }
        }
        return duplicates;
    }
}
