package io.github.blackshadowhrd.poiloot;

import io.github.blackshadowhrd.poiloot.command.PoiLootCommand;
import io.github.blackshadowhrd.poiloot.database.DatabaseManager;
import io.github.blackshadowhrd.poiloot.listener.LootPointInteractionListener;
import io.github.blackshadowhrd.poiloot.listener.PrivateLootInventoryListener;
import io.github.blackshadowhrd.poiloot.repository.ClaimRepository;
import io.github.blackshadowhrd.poiloot.repository.LootPointRepository;
import io.github.blackshadowhrd.poiloot.service.ClaimService;
import io.github.blackshadowhrd.poiloot.service.LootGenerationService;
import io.github.blackshadowhrd.poiloot.service.LootPointLookupService;
import io.github.blackshadowhrd.poiloot.service.LootPointPersistenceService;
import io.github.blackshadowhrd.poiloot.service.LootPointRegistrar;
import io.github.blackshadowhrd.poiloot.service.LootSessionService;
import io.github.blackshadowhrd.poiloot.service.PrivateInventoryService;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.java.JavaPlugin;

public final class PoiLootPlugin extends JavaPlugin {

    private DatabaseManager databaseManager;

    @Override
    public void onEnable() {
        try {
            databaseManager = new DatabaseManager(getDataFolder().toPath().resolve("poiloot.db"), getLogger());
            databaseManager.initialize(getDataFolder().toPath().resolve("poiloot.db"));
        } catch (RuntimeException exception) {
            getLogger().log(java.util.logging.Level.SEVERE,
                    "POILoot database initialization failed; disabling plugin", exception);
            if (databaseManager != null) {
                databaseManager.shutdown();
            }
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        LootPointRepository lootPointRepository = new LootPointRepository(databaseManager);
        ClaimRepository claimRepository = new ClaimRepository(databaseManager);
        LootPointPersistenceService persistenceService = new LootPointPersistenceService(lootPointRepository);
        ClaimService claimService = new ClaimService(claimRepository, getLogger());
        try {
            persistenceService.load();
            claimService.load();
        } catch (RuntimeException exception) {
            getLogger().log(java.util.logging.Level.SEVERE,
                    "POILoot database state loading failed; disabling plugin", exception);
            databaseManager.shutdown();
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        LootPointLookupService lookupService = new LootPointLookupService(this, persistenceService);
        LootPointRegistrar registrar = new LootPointRegistrar(
                this,
                lookupService,
                persistenceService,
                claimService
        );
        LootGenerationService generationService = new LootGenerationService(getServer());
        PrivateInventoryService inventoryService = new PrivateInventoryService(getServer());
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
                new PrivateLootInventoryListener(this, claimService, sessionService),
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

    @Override
    public void onDisable() {
        if (databaseManager != null) {
            databaseManager.shutdown();
        }
    }
}
