package io.github.blackshadowhrd.poiloot;

import io.github.blackshadowhrd.poiloot.command.PoiLootCommand;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class PoiLootPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        PluginCommand pluginCommand = Objects.requireNonNull(getCommand("poiloot"));
        PoiLootCommand command = new PoiLootCommand(this);
        pluginCommand.setExecutor(command);
        pluginCommand.setTabCompleter(command);
    }
}
