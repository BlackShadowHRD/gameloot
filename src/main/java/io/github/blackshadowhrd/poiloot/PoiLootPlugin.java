package io.github.blackshadowhrd.poiloot;

import io.github.blackshadowhrd.poiloot.command.PoiLootCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class PoiLootPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        Objects.requireNonNull(getCommand("poiloot")).setExecutor(new PoiLootCommand(this));
    }
}
