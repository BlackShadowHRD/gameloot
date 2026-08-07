package io.github.blackshadowhrd.gameloot.target;

import io.github.blackshadowhrd.gameloot.model.LootPoint;
import org.bukkit.Location;

public sealed interface LootPointTarget permits BlockLootPointTarget, EntityLootPointTarget {

    LootPoint lootPoint();

    Location location();
}
