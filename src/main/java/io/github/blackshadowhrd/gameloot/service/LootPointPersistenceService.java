package io.github.blackshadowhrd.gameloot.service;

import io.github.blackshadowhrd.gameloot.repository.LootPointRepository;
import io.github.blackshadowhrd.gameloot.repository.model.LootPointRecord;
import io.github.blackshadowhrd.gameloot.repository.model.LootPointDeletion;
import io.github.blackshadowhrd.gameloot.repository.model.ShelfRewardItem;

import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class LootPointPersistenceService {

    private final LootPointRepository repository;
    private final Map<UUID, LootPointRecord> records = new ConcurrentHashMap<>();
    private final Map<UUID, List<ShelfRewardItem>> shelfRewards = new ConcurrentHashMap<>();

    public LootPointPersistenceService(LootPointRepository repository) {
        this.repository = repository;
    }

    public void load() {
        records.clear();
        repository.findAllBlocking().forEach(record -> records.put(record.id(), record));
        shelfRewards.clear();
        repository.findAllShelfRewardsBlocking().forEach((id, rewards) ->
                shelfRewards.put(id, List.copyOf(rewards)));
    }

    public Optional<LootPointRecord> find(UUID id) {
        return Optional.ofNullable(records.get(id));
    }

    public List<LootPointRecord> records() {
        return List.copyOf(records.values());
    }

    public CompletableFuture<Boolean> insert(LootPointRecord record) {
        return repository.insert(record).thenApply(inserted -> {
            if (inserted) {
                records.put(record.id(), record);
            }
            return inserted;
        });
    }

    public CompletableFuture<Boolean> insertShelf(LootPointRecord record, List<ShelfRewardItem> rewards) {
        return repository.insertShelf(record, rewards).thenApply(inserted -> {
            if (inserted) {
                records.put(record.id(), record);
                shelfRewards.put(record.id(), List.copyOf(rewards));
            }
            return inserted;
        });
    }

    public List<ShelfRewardItem> shelfRewards(UUID id) {
        return shelfRewards.getOrDefault(id, List.of());
    }

    public CompletableFuture<Optional<LootPointDeletion>> delete(UUID id) {
        return repository.delete(id).thenApply(deleted -> {
            deleted.ifPresent(record -> records.remove(id));
            deleted.ifPresent(record -> shelfRewards.remove(id));
            return deleted;
        });
    }

    public CompletableFuture<Boolean> restore(LootPointDeletion deletion) {
        return repository.restore(deletion).thenApply(restored -> {
            if (restored) {
                records.put(deletion.lootPoint().id(), deletion.lootPoint());
                shelfRewards.put(deletion.lootPoint().id(), deletion.shelfRewards());
            }
            return restored;
        });
    }

    public CompletableFuture<Optional<LootPointRecord>> migrateLegacy(LootPointRecord record) {
        return insert(record).thenCompose(inserted -> {
            if (inserted) {
                return CompletableFuture.completedFuture(Optional.of(record));
            }
            return repository.findById(record.id()).thenApply(found -> {
                found.ifPresent(existing -> records.put(existing.id(), existing));
                return found;
            });
        });
    }
}
