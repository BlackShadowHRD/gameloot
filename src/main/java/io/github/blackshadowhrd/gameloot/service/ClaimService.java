package io.github.blackshadowhrd.gameloot.service;

import io.github.blackshadowhrd.gameloot.repository.ClaimRepository;
import io.github.blackshadowhrd.gameloot.repository.model.ClaimRecord;

import java.time.Clock;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ClaimService {

    private final ClaimRepository repository;
    private final Logger logger;
    private final Clock clock;
    private final Map<ClaimKey, ClaimState> claims = new ConcurrentHashMap<>();
    private final Object claimOperationGate = new Object();
    private final AtomicBoolean resetAllInProgress = new AtomicBoolean();

    public ClaimService(ClaimRepository repository, Logger logger) {
        this(repository, logger, Clock.systemUTC());
    }

    ClaimService(ClaimRepository repository, Logger logger, Clock clock) {
        this.repository = repository;
        this.logger = logger;
        this.clock = clock;
    }

    public void load() {
        claims.clear();
        repository.findAllBlocking().forEach(claim -> claims.put(
                new ClaimKey(claim.playerId(), claim.lootPointId()),
                ClaimState.PERSISTED
        ));
    }

    public boolean isClaimed(UUID playerId, UUID lootPointId) {
        return claims.containsKey(new ClaimKey(playerId, lootPointId));
    }

    public boolean hasPersistedClaim(UUID playerId, UUID lootPointId) {
        return claims.get(new ClaimKey(playerId, lootPointId)) == ClaimState.PERSISTED;
    }

    public int countPersistedClaims(UUID lootPointId) {
        return (int) claims.entrySet().stream()
                .filter(entry -> entry.getKey().lootPointId().equals(lootPointId))
                .filter(entry -> entry.getValue() == ClaimState.PERSISTED)
                .count();
    }

    public CompletableFuture<Boolean> markClaimed(UUID playerId, UUID lootPointId) {
        ClaimKey key = new ClaimKey(playerId, lootPointId);
        CompletableFuture<Boolean> insertion;
        synchronized (claimOperationGate) {
            if (resetAllInProgress.get() || claims.putIfAbsent(key, ClaimState.PENDING) != null) {
                return CompletableFuture.completedFuture(false);
            }
            ClaimRecord claim = new ClaimRecord(playerId, lootPointId, clock.millis());
            insertion = repository.insert(claim);
        }
        return insertion.handle((inserted, exception) -> {
            if (exception != null) {
                claims.put(key, ClaimState.FAILED);
                logger.log(Level.SEVERE, "Failed to persist claim for player " + playerId
                        + " and loot point " + lootPointId + "; access remains blocked until restart", exception);
                return false;
            }

            claims.put(key, ClaimState.PERSISTED);
            return inserted;
        });
    }

    public void removeClaimsForLootPoint(UUID lootPointId) {
        claims.keySet().removeIf(key -> key.lootPointId().equals(lootPointId));
    }

    public CompletableFuture<ClaimResetResult> resetClaim(UUID playerId, UUID lootPointId) {
        return repository.delete(playerId, lootPointId).thenApply(deleted -> resetCache(
                deleted,
                key -> key.playerId().equals(playerId) && key.lootPointId().equals(lootPointId)
        ));
    }

    public CompletableFuture<ClaimResetResult> resetPlayer(UUID playerId) {
        return repository.deleteByPlayer(playerId).thenApply(deleted -> resetCache(
                deleted,
                key -> key.playerId().equals(playerId)
        ));
    }

    public CompletableFuture<ClaimResetResult> resetLootPoint(UUID lootPointId) {
        return repository.deleteByLootPoint(lootPointId).thenApply(deleted -> resetCache(
                deleted,
                key -> key.lootPointId().equals(lootPointId)
        ));
    }

    public CompletableFuture<ClaimResetResult> resetAll() {
        CompletableFuture<Integer> deletion;
        synchronized (claimOperationGate) {
            if (!resetAllInProgress.compareAndSet(false, true)) {
                return CompletableFuture.failedFuture(new IllegalStateException("Claim reset already in progress"));
            }
            deletion = repository.deleteAll();
        }
        return deletion.thenApply(deleted -> {
            Set<ClaimKey> removed = Set.copyOf(claims.keySet());
            claims.clear();
            return new ClaimResetResult(deleted, removed);
        });
    }

    void finishResetAll() {
        resetAllInProgress.set(false);
    }

    private ClaimResetResult resetCache(int deleted, java.util.function.Predicate<ClaimKey> predicate) {
        Set<ClaimKey> removed = new HashSet<>();
        claims.entrySet().removeIf(entry -> {
            boolean remove = entry.getValue() != ClaimState.PENDING && predicate.test(entry.getKey());
            if (remove) {
                removed.add(entry.getKey());
            }
            return remove;
        });
        return new ClaimResetResult(deleted, removed);
    }

    public record ClaimKey(UUID playerId, UUID lootPointId) {
    }

    public record ClaimResetResult(int deletedClaims, Set<ClaimKey> resetClaims) {

        public ClaimResetResult {
            resetClaims = Set.copyOf(resetClaims);
        }
    }

    private enum ClaimState {
        PENDING,
        PERSISTED,
        FAILED
    }
}
