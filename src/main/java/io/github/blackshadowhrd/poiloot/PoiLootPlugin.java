package io.github.blackshadowhrd.poiloot;

import io.github.blackshadowhrd.poiloot.command.PoiLootCommand;
import io.github.blackshadowhrd.poiloot.listener.LootPointInteractionListener;
import io.github.blackshadowhrd.poiloot.listener.PrivateLootInventoryListener;
import io.github.blackshadowhrd.poiloot.service.InMemoryClaimService;
import io.github.blackshadowhrd.poiloot.service.LootGenerationService;
import io.github.blackshadowhrd.poiloot.service.LootPointLookupService;
import io.github.blackshadowhrd.poiloot.service.LootPointRegistrar;
import io.github.blackshadowhrd.poiloot.service.LootSessionService;
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
        InMemoryClaimService claimService = new InMemoryClaimService();
        LootSessionService sessionService = new LootSessionService(inventoryService);
        PoiLootCommand command = new PoiLootCommand(this, registrar, lookupService, generationService);

        getServer().getPluginManager().registerEvents(
                new LootPointInteractionListener(
                        this,
                        lookupService,
                        generationService,
                        inventoryService,
                        claimService,
                        sessionService
                ),
                this
        );
        getServer().getPluginManager().registerEvents(
                new PrivateLootInventoryListener(claimService, sessionService),
                this
        );

        getLifecycleManager().registerEventHandler(
                LifecycleEvents.COMMANDS,
                event -> event.registrar().register(
                        command.createCommand(),
                        PoiLootCommand.DESCRIPTION
                )
        );
    }
}
