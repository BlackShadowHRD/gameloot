package io.github.blackshadowhrd.gameloot;

import io.github.blackshadowhrd.gameloot.command.GameLootCommand;
import io.github.blackshadowhrd.gameloot.database.DatabaseManager;
import io.github.blackshadowhrd.gameloot.listener.LootPointInteractionListener;
import io.github.blackshadowhrd.gameloot.listener.LootPointProtectionListener;
import io.github.blackshadowhrd.gameloot.listener.PrivateLootInventoryListener;
import io.github.blackshadowhrd.gameloot.repository.ClaimRepository;
import io.github.blackshadowhrd.gameloot.repository.LootPointRepository;
import io.github.blackshadowhrd.gameloot.repository.ValidationRepository;
import io.github.blackshadowhrd.gameloot.service.ClaimService;
import io.github.blackshadowhrd.gameloot.service.ClaimAdministrationService;
import io.github.blackshadowhrd.gameloot.service.LootGenerationService;
import io.github.blackshadowhrd.gameloot.service.LootPointLookupService;
import io.github.blackshadowhrd.gameloot.service.LootPointListingService;
import io.github.blackshadowhrd.gameloot.service.LootPointCsvExporter;
import io.github.blackshadowhrd.gameloot.service.LootPointCsvExportService;
import io.github.blackshadowhrd.gameloot.service.LootPointPersistenceService;
import io.github.blackshadowhrd.gameloot.service.LootPointProtectionService;
import io.github.blackshadowhrd.gameloot.service.LootPointRegistrar;
import io.github.blackshadowhrd.gameloot.service.LootSessionService;
import io.github.blackshadowhrd.gameloot.service.LootTableCatalog;
import io.github.blackshadowhrd.gameloot.service.GameLootLootTableDiscovery;
import io.github.blackshadowhrd.gameloot.service.PrivateInventoryService;
import io.github.blackshadowhrd.gameloot.service.LootPointTargetResolver;
import io.github.blackshadowhrd.gameloot.service.ShelfRewardService;
import io.github.blackshadowhrd.gameloot.service.ValidationService;
import io.github.blackshadowhrd.gameloot.service.ConfirmationService;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Path;
import java.time.Duration;

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
        ValidationRepository validationRepository = new ValidationRepository(databaseManager);
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

        LootPointTargetResolver targetResolver = new LootPointTargetResolver();
        LootPointLookupService lookupService = new LootPointLookupService(this, persistenceService, targetResolver);
        LootPointListingService listingService = new LootPointListingService(this, lootPointRepository, targetResolver);
        LootPointCsvExportService csvExportService = new LootPointCsvExportService(
                this, listingService, new LootPointCsvExporter(getDataFolder().toPath()));
        ConfirmationService confirmationService = new ConfirmationService(Duration.ofSeconds(30));
        LootPointProtectionService protectionService = new LootPointProtectionService(lookupService);
        LootPointRegistrar registrar = new LootPointRegistrar(
                this,
                lookupService,
                persistenceService,
                claimService
        );
        LootGenerationService generationService = new LootGenerationService(getServer());
        LootTableCatalog lootTableCatalog = new LootTableCatalog(this, new GameLootLootTableDiscovery());
        ValidationService validationService = new ValidationService(
                this, persistenceService, validationRepository, lookupService, targetResolver, generationService);
        PrivateInventoryService inventoryService = new PrivateInventoryService(getServer());
        LootSessionService sessionService = new LootSessionService(inventoryService);
        ShelfRewardService shelfRewardService = new ShelfRewardService(this, persistenceService, claimService);
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
                claimAdministrationService,
                shelfRewardService,
                validationService,
                lootTableCatalog,
                listingService,
                confirmationService,
                csvExportService
        );

        getServer().getPluginManager().registerEvents(
                new LootPointInteractionListener(
                        this,
                        protectionService,
                        generationService,
                        inventoryService,
                        claimService,
                        sessionService,
                        shelfRewardService
                ),
                this
        );
        getServer().getPluginManager().registerEvents(
                new LootPointProtectionListener(protectionService),
                this
        );
        getServer().getPluginManager().registerEvents(
                new PrivateLootInventoryListener(this, claimService, sessionService),
                this
        );
        getServer().getPluginManager().registerEvents(lootTableCatalog, this);
        lootTableCatalog.refresh();
        getServer().getScheduler().runTaskTimer(this, confirmationService::purgeExpired, 20L, 20L);

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
