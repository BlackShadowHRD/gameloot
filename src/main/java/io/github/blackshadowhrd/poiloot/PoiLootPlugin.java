package io.github.blackshadowhrd.poiloot;

import io.github.blackshadowhrd.poiloot.command.PoiLootCommand;
import io.github.blackshadowhrd.poiloot.service.LootGenerationService;
import io.github.blackshadowhrd.poiloot.service.LootPointLookupService;
import io.github.blackshadowhrd.poiloot.service.LootPointRegistrar;
import io.github.blackshadowhrd.poiloot.service.PrivateInventoryService;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.java.JavaPlugin;

public final class PoiLootPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        LootPointLookupService lookupService = new LootPointLookupService(this);
        LootPointRegistrar registrar = new LootPointRegistrar(this, lookupService);
        LootGenerationService generationService = new LootGenerationService(getServer());
        PrivateInventoryService inventoryService = new PrivateInventoryService(getServer());
        PoiLootCommand command = new PoiLootCommand(this, registrar, lookupService, generationService);

        getLifecycleManager().registerEventHandler(
                LifecycleEvents.COMMANDS,
                event -> event.registrar().register(
                        command.createCommand(),
                        PoiLootCommand.DESCRIPTION
                )
        );
    }
}
