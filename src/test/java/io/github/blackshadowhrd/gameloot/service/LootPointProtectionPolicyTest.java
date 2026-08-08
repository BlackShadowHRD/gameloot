package io.github.blackshadowhrd.gameloot.service;

import org.bukkit.GameMode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LootPointProtectionPolicyTest {

    @Test
    void pistonMovementIsBlockedOnlyWhenAProtectedTargetIsIncluded() {
        Set<Integer> protectedTargets = Set.of(2);

        assertTrue(LootPointProtectionPolicy.containsProtected(List.of(1, 2, 3), protectedTargets::contains));
        assertFalse(LootPointProtectionPolicy.containsProtected(List.of(1, 3), protectedTargets::contains));
    }

    @Test
    void inventoryTransferIsBlockedInEitherDirection() {
        assertTrue(LootPointProtectionPolicy.blocksInventoryTransfer(true, false));
        assertTrue(LootPointProtectionPolicy.blocksInventoryTransfer(false, true));
        assertTrue(LootPointProtectionPolicy.blocksInventoryTransfer(true, true));
        assertFalse(LootPointProtectionPolicy.blocksInventoryTransfer(false, false));
    }

    @Test
    void lootInteractionAppliesToPlayableModesButLeavesSpectatorsVanilla() {
        assertTrue(LootPointProtectionPolicy.handlesLootInteraction(GameMode.SURVIVAL));
        assertTrue(LootPointProtectionPolicy.handlesLootInteraction(GameMode.ADVENTURE));
        assertTrue(LootPointProtectionPolicy.handlesLootInteraction(GameMode.CREATIVE));
        assertFalse(LootPointProtectionPolicy.handlesLootInteraction(GameMode.SPECTATOR));
    }
}
