package io.github.blackshadowhrd.gameloot.target;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public record LootPointResolution(
        boolean marked,
        CompletableFuture<Optional<LootPointTarget>> target
) {
}
