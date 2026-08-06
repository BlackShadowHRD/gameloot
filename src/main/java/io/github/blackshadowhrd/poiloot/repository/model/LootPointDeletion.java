package io.github.blackshadowhrd.poiloot.repository.model;

import java.util.List;

public record LootPointDeletion(LootPointRecord lootPoint, List<ClaimRecord> claims) {

    public LootPointDeletion {
        claims = List.copyOf(claims);
    }
}
