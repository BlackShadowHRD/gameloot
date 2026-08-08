package io.github.blackshadowhrd.gameloot.repository.model;

import java.util.List;

public record LootPointDeletion(
        LootPointRecord lootPoint,
        List<ClaimRecord> claims,
        List<ShelfRewardItem> shelfRewards
) {

    public LootPointDeletion {
        claims = List.copyOf(claims);
        shelfRewards = List.copyOf(shelfRewards);
    }
}
