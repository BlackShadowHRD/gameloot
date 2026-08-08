package io.github.blackshadowhrd.gameloot.service;

import org.bukkit.GameMode;

import java.util.Collection;
import java.util.function.Predicate;

public final class LootPointProtectionPolicy {

    private LootPointProtectionPolicy() {
    }

    public static <T> boolean containsProtected(Collection<T> targets, Predicate<T> isProtected) {
        return targets.stream().anyMatch(isProtected);
    }

    public static boolean blocksInventoryTransfer(boolean sourceProtected, boolean destinationProtected) {
        return sourceProtected || destinationProtected;
    }

    public static boolean handlesLootInteraction(GameMode gameMode) {
        return gameMode != GameMode.SPECTATOR;
    }
}
