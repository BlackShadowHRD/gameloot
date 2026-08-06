package io.github.blackshadowhrd.poiloot;

import io.github.blackshadowhrd.poiloot.command.PoiLootCommand;
import io.github.blackshadowhrd.poiloot.loot.LootPointRegistrar;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.java.JavaPlugin;

public final class PoiLootPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        LootPointRegistrar registrar = new LootPointRegistrar(this);
        PoiLootCommand command = new PoiLootCommand(this, registrar);

        getLifecycleManager().registerEventHandler(
                LifecycleEvents.COMMANDS,
                event -> event.registrar().register(
                        command.createCommand(),
                        PoiLootCommand.DESCRIPTION
                )
        );
    }
}
