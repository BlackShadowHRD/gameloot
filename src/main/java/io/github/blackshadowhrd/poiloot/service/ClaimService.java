package io.github.blackshadowhrd.poiloot.service;

import io.github.blackshadowhrd.poiloot.repository.ClaimRepository;
import io.github.blackshadowhrd.poiloot.repository.model.ClaimRecord;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ClaimService {

    private final ClaimRepository repository;
    private final Logger logger;
    private final Clock clock;
    private final Map<ClaimKey, ClaimState> claims = new ConcurrentHashMap<>();

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

    public CompletableFuture<Boolean> markClaimed(UUID playerId, UUID lootPointId) {
        ClaimKey key = new ClaimKey(playerId, lootPointId);
        if (claims.putIfAbsent(key, ClaimState.PENDING) != null) {
            return CompletableFuture.completedFuture(false);
        }

        ClaimRecord claim = new ClaimRecord(playerId, lootPointId, clock.millis());
        return repository.insert(claim).handle((inserted, exception) -> {
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

    private record ClaimKey(UUID playerId, UUID lootPointId) {
    }

    private enum ClaimState {
        PENDING,
        PERSISTED,
        FAILED
    }
}
