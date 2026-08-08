package io.github.blackshadowhrd.gameloot.model;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

public enum LootPointTargetType {
    CHEST(LootPointType.BLOCK_CONTAINER, LootMode.LOOT_TABLE, "Chest", Material.CHEST),
    TRAPPED_CHEST(LootPointType.BLOCK_CONTAINER, LootMode.LOOT_TABLE, "Trapped Chest", Material.TRAPPED_CHEST),
    BARREL(LootPointType.BLOCK_CONTAINER, LootMode.LOOT_TABLE, "Barrel", Material.BARREL),
    COPPER_CHEST(LootPointType.BLOCK_CONTAINER, LootMode.LOOT_TABLE, "Copper Chest",
            Material.COPPER_CHEST, Material.EXPOSED_COPPER_CHEST, Material.WEATHERED_COPPER_CHEST,
            Material.OXIDIZED_COPPER_CHEST, Material.WAXED_COPPER_CHEST,
            Material.WAXED_EXPOSED_COPPER_CHEST, Material.WAXED_WEATHERED_COPPER_CHEST,
            Material.WAXED_OXIDIZED_COPPER_CHEST),
    SHULKER_BOX(LootPointType.BLOCK_CONTAINER, LootMode.LOOT_TABLE, "Shulker Box",
            Material.SHULKER_BOX, Material.WHITE_SHULKER_BOX, Material.ORANGE_SHULKER_BOX,
            Material.MAGENTA_SHULKER_BOX, Material.LIGHT_BLUE_SHULKER_BOX, Material.YELLOW_SHULKER_BOX,
            Material.LIME_SHULKER_BOX, Material.PINK_SHULKER_BOX, Material.GRAY_SHULKER_BOX,
            Material.LIGHT_GRAY_SHULKER_BOX, Material.CYAN_SHULKER_BOX, Material.PURPLE_SHULKER_BOX,
            Material.BLUE_SHULKER_BOX, Material.BROWN_SHULKER_BOX, Material.GREEN_SHULKER_BOX,
            Material.RED_SHULKER_BOX, Material.BLACK_SHULKER_BOX),
    CHEST_MINECART(LootPointType.CHEST_MINECART, LootMode.LOOT_TABLE, "Chest Minecart", EntityType.CHEST_MINECART),
    SHELF(LootPointType.SHELF, LootMode.FIXED, "Shelf",
            Material.OAK_SHELF, Material.SPRUCE_SHELF, Material.BIRCH_SHELF, Material.JUNGLE_SHELF,
            Material.ACACIA_SHELF, Material.DARK_OAK_SHELF, Material.MANGROVE_SHELF,
            Material.CHERRY_SHELF, Material.PALE_OAK_SHELF, Material.BAMBOO_SHELF,
            Material.CRIMSON_SHELF, Material.WARPED_SHELF);

    private final LootPointType persistedType;
    private final LootMode lootMode;
    private final String displayName;
    private final Set<Material> materials;
    private final EntityType entityType;

    LootPointTargetType(LootPointType persistedType, LootMode lootMode, String displayName, Material... materials) {
        this.persistedType = persistedType;
        this.lootMode = lootMode;
        this.displayName = displayName;
        this.materials = Set.copyOf(Arrays.asList(materials));
        entityType = null;
    }

    LootPointTargetType(LootPointType persistedType, LootMode lootMode, String displayName, EntityType entityType) {
        this.persistedType = persistedType;
        this.lootMode = lootMode;
        this.displayName = displayName;
        materials = Set.of();
        this.entityType = entityType;
    }

    public LootPointType persistedType() { return persistedType; }
    public LootMode lootMode() { return lootMode; }
    public String displayName() { return displayName; }

    public static Optional<LootPointTargetType> fromMaterial(Material material) {
        return Arrays.stream(values()).filter(type -> type.materials.contains(material)).findFirst();
    }

    public static Optional<LootPointTargetType> fromEntityType(EntityType entityType) {
        return Arrays.stream(values()).filter(type -> type.entityType == entityType).findFirst();
    }

    public enum LootMode { LOOT_TABLE, FIXED }
}
