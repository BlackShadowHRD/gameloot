package io.github.blackshadowhrd.poiloot.service;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryClaimService {

    private final Set<ClaimKey> claims = ConcurrentHashMap.newKeySet();

    public boolean isClaimed(UUID playerId, UUID lootPointId) {
        return claims.contains(new ClaimKey(playerId, lootPointId));
    }

    public boolean markClaimed(UUID playerId, UUID lootPointId) {
        return claims.add(new ClaimKey(playerId, lootPointId));
    }

    private record ClaimKey(UUID playerId, UUID lootPointId) {
    }
}
