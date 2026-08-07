package io.github.blackshadowhrd.gameloot.target;

import io.github.blackshadowhrd.gameloot.model.LootPoint;
import io.github.blackshadowhrd.gameloot.model.LootPointType;
import org.bukkit.Location;

import java.util.Objects;
import java.util.Optional;

public record LootPointInspection(
        Optional<LootPoint> lootPoint,
        boolean markerPresent,
        LootPointType type,
        String displayType,
        Location location
) {

    public LootPointInspection {
        Objects.requireNonNull(lootPoint, "lootPoint");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(displayType, "displayType");
        location = Objects.requireNonNull(location, "location").clone();
    }

    @Override
    public Location location() {
        return location.clone();
    }
}
