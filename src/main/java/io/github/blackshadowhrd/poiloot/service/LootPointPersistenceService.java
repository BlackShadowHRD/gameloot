package io.github.blackshadowhrd.poiloot.service;

import io.github.blackshadowhrd.poiloot.repository.LootPointRepository;
import io.github.blackshadowhrd.poiloot.repository.model.LootPointRecord;
import io.github.blackshadowhrd.poiloot.repository.model.LootPointDeletion;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class LootPointPersistenceService {

    private final LootPointRepository repository;
    private final Map<UUID, LootPointRecord> records = new ConcurrentHashMap<>();

    public LootPointPersistenceService(LootPointRepository repository) {
        this.repository = repository;
    }

    public void load() {
        records.clear();
        repository.findAllBlocking().forEach(record -> records.put(record.id(), record));
    }

    public Optional<LootPointRecord> find(UUID id) {
        return Optional.ofNullable(records.get(id));
    }

    public CompletableFuture<Boolean> insert(LootPointRecord record) {
        return repository.insert(record).thenApply(inserted -> {
            if (inserted) {
                records.put(record.id(), record);
            }
            return inserted;
        });
    }

    public CompletableFuture<Optional<LootPointDeletion>> delete(UUID id) {
        return repository.delete(id).thenApply(deleted -> {
            deleted.ifPresent(record -> records.remove(id));
            return deleted;
        });
    }

    public CompletableFuture<Boolean> restore(LootPointDeletion deletion) {
        return repository.restore(deletion).thenApply(restored -> {
            if (restored) {
                records.put(deletion.lootPoint().id(), deletion.lootPoint());
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
