package io.github.blackshadowhrd.gameloot.model;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LootPointTargetTypeTest {

    @Test
    void resolvesEveryExplicitBlockTarget() {
        assertEquals(LootPointTargetType.CHEST, type(Material.CHEST));
        assertEquals(LootPointTargetType.TRAPPED_CHEST, type(Material.TRAPPED_CHEST));
        assertEquals(LootPointTargetType.BARREL, type(Material.BARREL));

        List.of(Material.COPPER_CHEST, Material.EXPOSED_COPPER_CHEST,
                Material.WEATHERED_COPPER_CHEST, Material.OXIDIZED_COPPER_CHEST,
                Material.WAXED_COPPER_CHEST, Material.WAXED_EXPOSED_COPPER_CHEST,
                Material.WAXED_WEATHERED_COPPER_CHEST, Material.WAXED_OXIDIZED_COPPER_CHEST)
                .forEach(material -> assertEquals(LootPointTargetType.COPPER_CHEST, type(material)));

        java.util.Arrays.stream(Material.values())
                .filter(material -> !material.isLegacy() && material.name().endsWith("SHULKER_BOX"))
                .forEach(material -> assertEquals(LootPointTargetType.SHULKER_BOX, type(material)));
        java.util.Arrays.stream(Material.values())
                .filter(material -> material.name().endsWith("_SHELF") && material != Material.BOOKSHELF
                        && material != Material.CHISELED_BOOKSHELF)
                .forEach(material -> assertEquals(LootPointTargetType.SHELF, type(material)));
    }

    @Test
    void resolvesChestMinecartAndRejectsUnsupportedTargets() {
        assertEquals(LootPointTargetType.CHEST_MINECART,
                LootPointTargetType.fromEntityType(EntityType.CHEST_MINECART).orElseThrow());
        List.of(Material.DISPENSER, Material.DROPPER, Material.HOPPER, Material.FURNACE,
                Material.DECORATED_POT, Material.CHISELED_BOOKSHELF, Material.ENDER_CHEST)
                .forEach(material -> assertTrue(LootPointTargetType.fromMaterial(material).isEmpty()));
        assertTrue(LootPointTargetType.fromEntityType(EntityType.HOPPER_MINECART).isEmpty());
    }

    private LootPointTargetType type(Material material) {
        return LootPointTargetType.fromMaterial(material).orElseThrow();
    }
}
