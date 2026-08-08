package io.github.blackshadowhrd.gameloot;

import io.github.blackshadowhrd.gameloot.command.GameLootCommand;
import io.github.blackshadowhrd.gameloot.database.DatabaseManager;
import io.github.blackshadowhrd.gameloot.listener.LootPointInteractionListener;
import io.github.blackshadowhrd.gameloot.listener.PrivateLootInventoryListener;
import io.github.blackshadowhrd.gameloot.repository.ClaimRepository;
import io.github.blackshadowhrd.gameloot.repository.LootPointRepository;
import io.github.blackshadowhrd.gameloot.service.ClaimService;
import io.github.blackshadowhrd.gameloot.service.ClaimAdministrationService;
import io.github.blackshadowhrd.gameloot.service.LootGenerationService;
import io.github.blackshadowhrd.gameloot.service.LootPointLookupService;
import io.github.blackshadowhrd.gameloot.service.LootPointPersistenceService;
import io.github.blackshadowhrd.gameloot.service.LootPointRegistrar;
import io.github.blackshadowhrd.gameloot.service.LootSessionService;
import io.github.blackshadowhrd.gameloot.service.PrivateInventoryService;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Path;

public final class GameLootPlugin extends JavaPlugin {

    private DatabaseManager databaseManager;

    @Override
    public void onEnable() {
        try {
            Path databasePath = getDataFolder().toPath().resolve("gameloot.db");
            Path legacyDatabasePath = getDataFolder().toPath().resolveSibling("POILoot").resolve("poiloot.db");
            DatabaseManager.migrateLegacyDatabase(legacyDatabasePath, databasePath, getLogger());
            databaseManager = new DatabaseManager(databasePath, getLogger());
            databaseManager.initialize(databasePath);
        } catch (RuntimeException exception) {
            getLogger().log(java.util.logging.Level.SEVERE,
                    "GameLoot database initialization failed; disabling plugin", exception);
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
                    "GameLoot database state loading failed; disabling plugin", exception);
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
        ClaimAdministrationService claimAdministrationService = new ClaimAdministrationService(
                this,
                claimService,
                sessionService
        );
        GameLootCommand command = new GameLootCommand(
                this,
                registrar,
                lookupService,
                generationService,
                claimAdministrationService
        );

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
                        GameLootCommand.DESCRIPTION
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
