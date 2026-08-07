package io.github.blackshadowhrd.gameloot.target;

import io.github.blackshadowhrd.gameloot.model.LootPoint;
import org.bukkit.Location;
import org.bukkit.entity.Entity;

import java.util.Objects;

public record EntityLootPointTarget(LootPoint lootPoint, Entity entity) implements LootPointTarget {

    public EntityLootPointTarget {
        Objects.requireNonNull(lootPoint, "lootPoint");
        Objects.requireNonNull(entity, "entity");
    }

    @Override
    public Location location() {
        return entity.getLocation();
    }
}
