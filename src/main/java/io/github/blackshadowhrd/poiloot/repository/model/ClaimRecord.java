package io.github.blackshadowhrd.poiloot.repository.model;

import java.util.UUID;

public record ClaimRecord(UUID playerId, UUID lootPointId, long claimedAt) {
}
