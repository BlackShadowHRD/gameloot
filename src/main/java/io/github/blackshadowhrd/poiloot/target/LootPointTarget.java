package io.github.blackshadowhrd.poiloot.target;

import io.github.blackshadowhrd.poiloot.model.LootPoint;
import org.bukkit.Location;

public sealed interface LootPointTarget permits BlockLootPointTarget, EntityLootPointTarget {

    LootPoint lootPoint();

    Location location();
}
