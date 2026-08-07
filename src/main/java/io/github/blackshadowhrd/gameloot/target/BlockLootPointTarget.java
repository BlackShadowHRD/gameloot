package io.github.blackshadowhrd.gameloot.target;

import io.github.blackshadowhrd.gameloot.model.LootPoint;
import org.bukkit.Location;
import org.bukkit.block.Block;

import java.util.Objects;

public record BlockLootPointTarget(LootPoint lootPoint, Block block) implements LootPointTarget {

    public BlockLootPointTarget {
        Objects.requireNonNull(lootPoint, "lootPoint");
        Objects.requireNonNull(block, "block");
    }

    @Override
    public Location location() {
        return block.getLocation();
    }
}
