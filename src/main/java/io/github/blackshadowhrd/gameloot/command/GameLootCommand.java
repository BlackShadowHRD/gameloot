package io.github.blackshadowhrd.gameloot.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.github.blackshadowhrd.gameloot.model.LootPoint;
import io.github.blackshadowhrd.gameloot.model.LootPointListEntry;
import io.github.blackshadowhrd.gameloot.model.Page;
import io.github.blackshadowhrd.gameloot.service.ClaimAdministrationService;
import io.github.blackshadowhrd.gameloot.service.ConfirmationService;
import io.github.blackshadowhrd.gameloot.service.LootGenerationService;
import io.github.blackshadowhrd.gameloot.service.LootPointLookupService;
import io.github.blackshadowhrd.gameloot.service.LootPointListingService;
import io.github.blackshadowhrd.gameloot.service.LootPointCsvExportService;
import io.github.blackshadowhrd.gameloot.service.LootPointRegistrar;
import io.github.blackshadowhrd.gameloot.service.LootTableCatalog;
import io.github.blackshadowhrd.gameloot.service.ShelfRewardService;
import io.github.blackshadowhrd.gameloot.service.ValidationService;
import io.github.blackshadowhrd.gameloot.model.LootPointType;
import io.github.blackshadowhrd.gameloot.target.LootPointInspection;
import io.github.blackshadowhrd.gameloot.validation.LootPointValidationResult;
import io.github.blackshadowhrd.gameloot.validation.ValidationReport;
import io.github.blackshadowhrd.gameloot.validation.ValidationStatus;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.PlayerProfileListResolver;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public final class GameLootCommand {

    public static final String DESCRIPTION = "Manages GameLoot loot points";

    private static final String ADMIN_PERMISSION = "gameloot.admin";
    private static final String LOOT_TABLE_ARGUMENT = "loot-table";
    private static final String PLAYER_ARGUMENT = "player";
    private static final String PAGE_ARGUMENT = "page";
    private static final String RESET_ALL_ACTION = "reset-all-claims";
    private static final int LIST_PAGE_SIZE = 10;
    private static final double TARGET_DISTANCE = 6;
    private static final Component SUPPORTED_CONTAINER_HELP =
            Component.text("Look at a supported container within 6 blocks.");

    private final Plugin plugin;
    private final LootPointRegistrar registrar;
    private final LootPointLookupService lookupService;
    private final LootGenerationService generationService;
    private final ClaimAdministrationService claimAdministrationService;
    private final ShelfRewardService shelfRewardService;
    private final ValidationService validationService;
    private final LootTableCatalog lootTableCatalog;
    private final LootPointListingService listingService;
    private final ConfirmationService confirmationService;
    private final LootPointCsvExportService csvExportService;

    public GameLootCommand(
            Plugin plugin,
            LootPointRegistrar registrar,
            LootPointLookupService lookupService,
            LootGenerationService generationService,
            ClaimAdministrationService claimAdministrationService,
            ShelfRewardService shelfRewardService,
            ValidationService validationService,
            LootTableCatalog lootTableCatalog,
            LootPointListingService listingService,
            ConfirmationService confirmationService,
            LootPointCsvExportService csvExportService
    ) {
        this.plugin = plugin;
        this.registrar = registrar;
        this.lookupService = lookupService;
        this.generationService = generationService;
        this.claimAdministrationService = claimAdministrationService;
        this.shelfRewardService = shelfRewardService;
        this.validationService = validationService;
        this.lootTableCatalog = lootTableCatalog;
        this.listingService = listingService;
        this.confirmationService = confirmationService;
        this.csvExportService = csvExportService;
    }

    public LiteralCommandNode<CommandSourceStack> createCommand() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("gameloot")
                .executes(this::help)
                .then(Commands.literal("version")
                        .executes(this::version))
                .then(Commands.literal("register")
                        .requires(GameLootCommand::hasAdminPermission)
                        .then(Commands.literal("shelf").executes(this::registerShelf))
                        .then(Commands.argument(LOOT_TABLE_ARGUMENT, ArgumentTypes.namespacedKey())
                                .suggests(this::suggestLootTables)
                                .executes(this::register)))
                .then(Commands.literal("deregister")
                        .requires(GameLootCommand::hasAdminPermission)
                        .executes(this::deregister))
                .then(Commands.literal("inspect")
                        .requires(GameLootCommand::hasAdminPermission)
                        .executes(this::inspect))
                .then(Commands.literal("claims")
                        .requires(GameLootCommand::hasAdminPermission)
                        .executes(this::claims))
                .then(Commands.literal("validate")
                        .requires(GameLootCommand::hasAdminPermission)
                        .executes(this::validate))
                .then(Commands.literal("list")
                        .requires(GameLootCommand::hasAdminPermission)
                        .executes(context -> list(context, 1))
                        .then(Commands.literal("csv").executes(this::exportCsv))
                        .then(Commands.argument(PAGE_ARGUMENT, IntegerArgumentType.integer(1))
                                .executes(context -> list(context,
                                        IntegerArgumentType.getInteger(context, PAGE_ARGUMENT)))))
                .then(Commands.literal("reset")
                        .requires(GameLootCommand::hasAdminPermission)
                        .then(Commands.literal("all")
                                .executes(this::requestResetAll)
                                .then(Commands.literal("confirm").executes(this::confirmResetAll)))
                        .then(Commands.literal("container")
                                .executes(this::resetContainer))
                        .then(Commands.literal("player")
                                .then(Commands.argument(PLAYER_ARGUMENT, ArgumentTypes.playerProfiles())
                                        .executes(this::resetPlayer)
                                        .then(Commands.literal("container")
                                                .executes(this::resetPlayerContainer)))));

        return root.build();
    }

    private int help(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();

        sender.sendMessage(Component.text("GameLoot commands:"));
        sender.sendMessage(Component.text("/gameloot version"));

        if (sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage(Component.text("/gameloot register <loot-table>"));
            sender.sendMessage(Component.text("/gameloot register shelf"));
            sender.sendMessage(Component.text("/gameloot deregister"));
            sender.sendMessage(Component.text("/gameloot inspect"));
            sender.sendMessage(Component.text("/gameloot claims"));
            sender.sendMessage(Component.text("/gameloot validate"));
            sender.sendMessage(Component.text("/gameloot list [page]"));
            sender.sendMessage(Component.text("/gameloot list csv"));
            sender.sendMessage(Component.text("/gameloot reset container"));
            sender.sendMessage(Component.text("/gameloot reset player <player>"));
            sender.sendMessage(Component.text("/gameloot reset player <player> container"));
            sender.sendMessage(Component.text("/gameloot reset all"));
        }

        return Command.SINGLE_SUCCESS;
    }

    private int version(CommandContext<CommandSourceStack> context) {
        context.getSource().getSender().sendMessage(Component.text(
                plugin.getPluginMeta().getName() + " " + plugin.getPluginMeta().getVersion()
        ));
        return Command.SINGLE_SUCCESS;
    }

    private int register(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can register loot points.", NamedTextColor.RED));
            return 0;
        }

        NamespacedKey lootTable = context.getArgument(LOOT_TABLE_ARGUMENT, NamespacedKey.class);

        if (generationService.resolveLootTable(lootTable).isEmpty()) {
            sender.sendMessage(Component.text("Unknown loot table: " + lootTable, NamedTextColor.RED));
            return 0;
        }
        registrar.register(player, lootTable).whenComplete((result, exception) ->
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (exception != null) {
                        sender.sendMessage(Component.text("Unable to register this loot point.", NamedTextColor.RED));
                        return;
                    }
                    sendRegistrationResult(sender, lootTable, result);
                })
        );
        return Command.SINGLE_SUCCESS;
    }

    private void sendRegistrationResult(
            CommandSender sender,
            NamespacedKey lootTable,
            LootPointRegistrar.Result result
    ) {
        switch (result) {
            case REGISTERED -> {
                sender.sendMessage(Component.text("Loot point registered with " + lootTable, NamedTextColor.GREEN));
            }
            case ALREADY_REGISTERED ->
                    sender.sendMessage(Component.text("That container is already a loot point.", NamedTextColor.YELLOW));
            case INVALID_TARGET -> sender.sendMessage(SUPPORTED_CONTAINER_HELP);
            case INVALID_SHELF_TARGET -> sender.sendMessage(Component.text(
                    "Look at a shelf within 6 blocks to register a fixed shelf reward.", NamedTextColor.YELLOW));
            case SHELF_REQUIRES_FIXED_REGISTRATION -> sender.sendMessage(Component.text(
                    "Shelves use /gameloot register shelf.", NamedTextColor.YELLOW));
            case EMPTY_SHELF -> sender.sendMessage(Component.text(
                    "Place at least one item stack on the shelf before registering it.", NamedTextColor.YELLOW));
            case PERSISTENCE_FAILURE ->
                    sender.sendMessage(Component.text("Unable to persist this loot point.", NamedTextColor.RED));
        }
    }

    private int registerShelf(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can register shelf loot points.", NamedTextColor.RED));
            return 0;
        }
        registrar.registerShelf(player).whenComplete((result, exception) -> runOnMainThread(() -> {
            if (exception != null) {
                sender.sendMessage(Component.text("Unable to register this shelf.", NamedTextColor.RED));
                return;
            }
            if (result == LootPointRegistrar.Result.REGISTERED) {
                sender.sendMessage(Component.text("Shelf loot point registered.", NamedTextColor.GREEN));
            } else {
                sendRegistrationResult(sender, null, result);
            }
        }));
        return Command.SINGLE_SUCCESS;
    }

    private int deregister(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can deregister loot points."));
            return 0;
        }

        registrar.deregister(player).whenComplete((result, exception) ->
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (exception != null) {
                        sender.sendMessage(Component.text("Unable to deregister this loot point.", NamedTextColor.RED));
                        return;
                    }
                    sendDeregistrationResult(sender, result);
                })
        );
        return Command.SINGLE_SUCCESS;
    }

    private void sendDeregistrationResult(CommandSender sender, LootPointRegistrar.DeregisterResult result) {
        switch (result) {
            case DEREGISTERED -> {
                sender.sendMessage(Component.text("Loot point deregistered."));
            }
            case NOT_REGISTERED ->
                    sender.sendMessage(Component.text("That container is not a registered loot point."));
            case INVALID_TARGET -> sender.sendMessage(SUPPORTED_CONTAINER_HELP);
            case MISSING_DATABASE_RECORD -> sender.sendMessage(Component.text(
                    "Loot point data is missing from the database. Check the server log.",
                    NamedTextColor.RED
            ));
            case PERSISTENCE_FAILURE ->
                    sender.sendMessage(Component.text("Unable to persist deregistration.", NamedTextColor.RED));
        }
    }

    private int inspect(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can inspect loot points."));
            return 0;
        }

        lookupService.inspectTarget(player, TARGET_DISTANCE).whenComplete((inspection, exception) ->
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (exception != null) {
                        sender.sendMessage(Component.text(
                                "Unable to inspect this loot point.",
                                NamedTextColor.RED
                        ));
                        return;
                    }
                    inspection.ifPresentOrElse(
                            value -> sendInspection(sender, value),
                            () -> sender.sendMessage(SUPPORTED_CONTAINER_HELP)
                    );
                })
        );
        return Command.SINGLE_SUCCESS;
    }

    private void sendInspection(CommandSender sender, LootPointInspection inspection) {
            sender.sendMessage(Component.text("Registered: " + (inspection.lootPoint().isPresent() ? "Yes" : "No")));
            sender.sendMessage(Component.text("Id: " + valueOrDash(
                    inspection.lootPoint().map(LootPoint::id).map(Object::toString).orElse(null)
            )));
            sender.sendMessage(Component.text("Type: " + inspection.displayType()));
            sender.sendMessage(Component.text("Loot table: " + valueOrDash(inspection.lootPoint()
                    .map(LootPoint::lootTable).map(key -> key == null ? null : key.asString()).orElse(null))));
            inspection.lootPoint().filter(point -> point.type() == LootPointType.SHELF).ifPresent(point -> {
                sender.sendMessage(Component.text("Loot mode: Fixed"));
                sender.sendMessage(Component.text("Reward slots: " + shelfRewardService.rewardSlots(point)));
            });
            sender.sendMessage(Component.text("Location: " + formatLocation(inspection)));
            if (inspection.markerPresent() && inspection.lootPoint().isEmpty()) {
                sender.sendMessage(Component.text(
                        "Diagnostic: the PDC marker has no matching database record. Check the server log.",
                        NamedTextColor.RED
                ));
            }
    }

    private int claims(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can inspect container claims.", NamedTextColor.RED));
            return 0;
        }

        withTargetLootPoint(player, lootPoint -> {
            sender.sendMessage(Component.text("Loot point: " + lootPoint.id()));
            sender.sendMessage(Component.text("Loot table: "
                    + (lootPoint.lootTable() == null ? "-" : lootPoint.lootTable())));
            sender.sendMessage(Component.text(
                    "Total claims: " + claimAdministrationService.claimCount(lootPoint.id())
            ));
            sender.sendMessage(Component.text(
                    "Claimed by you: " + (claimAdministrationService.hasClaim(
                            player.getUniqueId(),
                            lootPoint.id()
                    ) ? "Yes" : "No")
            ));
        });
        return Command.SINGLE_SUCCESS;
    }

    private int validate(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        sender.sendMessage(Component.text("GameLoot validation started...", NamedTextColor.YELLOW));
        validationService.validate().whenComplete((report, exception) -> runOnMainThread(() -> {
            if (exception != null) {
                sender.sendMessage(Component.text(
                        "GameLoot validation failed. Check the server log.", NamedTextColor.RED));
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "GameLoot validation failed", exception);
                return;
            }
            sendValidationReport(sender, report);
            logValidationDetails(report);
        }));
        return Command.SINGLE_SUCCESS;
    }

    private void sendValidationReport(CommandSender sender, ValidationReport report) {
        sender.sendMessage(Component.text("GameLoot validation complete", NamedTextColor.GREEN));
        sender.sendMessage(Component.text("Loot points: " + report.lootPoints().size()));
        sender.sendMessage(Component.text("Valid: " + report.count(ValidationStatus.VALID)));
        sender.sendMessage(Component.text("Warnings: " + report.count(ValidationStatus.WARNING)));
        sender.sendMessage(Component.text("Invalid: " + report.count(ValidationStatus.INVALID)));
        sender.sendMessage(Component.text("Unverified: " + report.count(ValidationStatus.UNVERIFIED)));
        sender.sendMessage(Component.text(
                "Database integrity: " + (report.databaseIntegrity().valid() ? "OK" : "FAILED"),
                report.databaseIntegrity().valid() ? NamedTextColor.GREEN : NamedTextColor.RED));
        report.issueCounts().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> sender.sendMessage(Component.text(
                        entry.getKey().description() + ": " + entry.getValue(),
                        issueColor(entry.getKey().status()))));
    }

    private void logValidationDetails(ValidationReport report) {
        report.lootPoints().stream()
                .filter(result -> result.status() == ValidationStatus.INVALID
                        || result.status() == ValidationStatus.WARNING)
                .forEach(result -> plugin.getLogger().warning(formatValidationResult(result)));
        report.databaseIntegrity().violations().forEach(violation ->
                plugin.getLogger().severe("GameLoot database integrity: " + violation));
    }

    private String formatValidationResult(LootPointValidationResult result) {
        var point = result.lootPoint();
        return "GameLoot validation " + result.status() + " for " + point.id()
                + " in world " + point.worldUuid() + " at (" + point.x() + ", " + point.y() + ", "
                + point.z() + "): " + result.issues();
    }

    private NamedTextColor issueColor(ValidationStatus status) {
        return switch (status) {
            case INVALID -> NamedTextColor.RED;
            case WARNING, UNVERIFIED -> NamedTextColor.YELLOW;
            case VALID -> NamedTextColor.GREEN;
        };
    }

    private int requestResetAll(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        confirmationService.request(senderKey(sender), RESET_ALL_ACTION);
        sender.sendMessage(Component.text(
                "Warning: this will delete every player claim for every registered GameLoot loot point.",
                NamedTextColor.RED));
        sender.sendMessage(Component.text("Loot-point registrations will NOT be removed.", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("Run /gameloot reset all confirm within 30 seconds to continue.",
                NamedTextColor.YELLOW));
        return Command.SINGLE_SUCCESS;
    }

    private int confirmResetAll(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        if (!confirmationService.consume(senderKey(sender), RESET_ALL_ACTION)) {
            sender.sendMessage(Component.text(
                    "No active reset-all confirmation. Run /gameloot reset all first.", NamedTextColor.RED));
            return 0;
        }
        claimAdministrationService.resetAll().whenComplete((deleted, exception) -> runOnMainThread(() -> {
            if (exception != null) {
                sender.sendMessage(Component.text("Unable to reset all claims. No success was reported.",
                        NamedTextColor.RED));
                plugin.getLogger().log(java.util.logging.Level.SEVERE,
                        "Failed to reset all claims for command sender " + senderKey(sender), exception);
                return;
            }
            sender.sendMessage(Component.text("Reset " + deleted + " claim" + (deleted == 1 ? "" : "s")
                    + ". Loot-point registrations were preserved.", NamedTextColor.GREEN));
        }));
        return Command.SINGLE_SUCCESS;
    }

    private int list(CommandContext<CommandSourceStack> context, int requestedPage) {
        CommandSender sender = context.getSource().getSender();
        listingService.list().whenComplete((entries, exception) -> runOnMainThread(() -> {
            if (exception != null) {
                sender.sendMessage(Component.text("Unable to list GameLoot loot points.", NamedTextColor.RED));
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "Failed to list loot points", exception);
                return;
            }
            try {
                sendListPage(sender, Page.of(entries, requestedPage, LIST_PAGE_SIZE));
            } catch (IllegalArgumentException pageError) {
                int pages = Math.max(1, (entries.size() + LIST_PAGE_SIZE - 1) / LIST_PAGE_SIZE);
                sender.sendMessage(Component.text("Page must be between 1 and " + pages + ".",
                        NamedTextColor.RED));
            }
        }));
        return Command.SINGLE_SUCCESS;
    }

    private int exportCsv(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        sender.sendMessage(Component.text("Exporting GameLoot loot points...", NamedTextColor.YELLOW));
        csvExportService.export().whenComplete((result, exception) -> runOnMainThread(() -> {
            if (exception != null) {
                sender.sendMessage(Component.text("Unable to export GameLoot loot points.", NamedTextColor.RED));
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "Failed to export loot points to CSV",
                        exception);
                return;
            }
            sender.sendMessage(Component.text("Exported " + result.exported() + " GameLoot loot point"
                    + (result.exported() == 1 ? "" : "s") + " to:", NamedTextColor.GREEN));
            sender.sendMessage(Component.text(result.relativePath(), NamedTextColor.AQUA));
        }));
        return Command.SINGLE_SUCCESS;
    }

    private void sendListPage(CommandSender sender, Page<LootPointListEntry> page) {
        sender.sendMessage(Component.text("GameLoot loot points — page " + page.page() + "/" + page.totalPages(),
                NamedTextColor.GOLD));
        if (page.totalEntries() == 0) {
            sender.sendMessage(Component.text("No registered loot points.", NamedTextColor.YELLOW));
            return;
        }
        int number = (page.page() - 1) * LIST_PAGE_SIZE + 1;
        for (LootPointListEntry entry : page.entries()) {
            String stale = entry.locationMayBeStale() ? " (persisted; may be stale)" : "";
            sender.sendMessage(Component.text("#" + number++ + " " + entry.targetType() + " — "
                    + entry.world() + ": " + entry.x() + " " + entry.y() + " " + entry.z() + stale));
            String loot = entry.shelf() ? "Fixed shelf reward"
                    : entry.lootTable() == null ? "-" : entry.lootTable().asString();
            Component details = Component.text("Loot: " + loot);
            if (entry.teleportCommand() != null) {
                details = details.append(Component.text("  [Teleport]", NamedTextColor.AQUA)
                        .clickEvent(ClickEvent.suggestCommand(entry.teleportCommand()))
                        .hoverEvent(HoverEvent.showText(Component.text("Click to teleport"))));
            }
            sender.sendMessage(details);
        }
        Component navigation = Component.empty();
        if (page.page() > 1) navigation = navigation.append(pageControl("Previous", page.page() - 1));
        if (page.page() > 1 && page.page() < page.totalPages()) navigation = navigation.append(Component.space());
        if (page.page() < page.totalPages()) navigation = navigation.append(pageControl("Next", page.page() + 1));
        if (!navigation.equals(Component.empty())) sender.sendMessage(navigation);
    }

    private Component pageControl(String label, int page) {
        return Component.text("[" + label + "]", NamedTextColor.AQUA)
                .clickEvent(ClickEvent.runCommand("/gameloot list " + page))
                .hoverEvent(HoverEvent.showText(Component.text("Go to page " + page)));
    }

    private String senderKey(CommandSender sender) {
        if (sender instanceof Player player) return "player:" + player.getUniqueId();
        if (sender instanceof ConsoleCommandSender) return "console";
        return sender.getClass().getName() + ':' + sender.getName();
    }

    private int resetContainer(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can reset container claims.", NamedTextColor.RED));
            return 0;
        }

        withTargetLootPoint(player, lootPoint -> claimAdministrationService.resetLootPoint(lootPoint.id())
                .whenComplete((deleted, exception) -> runOnMainThread(() -> {
                    if (exception != null) {
                        sender.sendMessage(Component.text("Unable to reset container claims.", NamedTextColor.RED));
                        return;
                    }
                    sender.sendMessage(Component.text(
                            "Reset " + deleted + " claim" + (deleted == 1 ? "" : "s") + " for this container.",
                            NamedTextColor.GREEN
                    ));
                })));
        return Command.SINGLE_SUCCESS;
    }

    private int resetPlayer(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        CommandSender sender = context.getSource().getSender();
        Optional<ResolvedPlayer> resolved = resolvePlayer(context);
        if (resolved.isEmpty()) {
            sender.sendMessage(Component.text("Resolve exactly one player.", NamedTextColor.RED));
            return 0;
        }

        ResolvedPlayer player = resolved.get();
        claimAdministrationService.resetPlayer(player.id()).whenComplete((deleted, exception) ->
                runOnMainThread(() -> {
                    if (exception != null) {
                        sender.sendMessage(Component.text("Unable to reset player claims.", NamedTextColor.RED));
                        return;
                    }
                    sender.sendMessage(Component.text(
                            "Reset " + deleted + " claim" + (deleted == 1 ? "" : "s")
                                    + " for " + player.displayName() + ".",
                            NamedTextColor.GREEN
                    ));
                })
        );
        return Command.SINGLE_SUCCESS;
    }

    private int resetPlayerContainer(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        CommandSender sender = context.getSource().getSender();
        if (!(sender instanceof Player executor)) {
            sender.sendMessage(Component.text(
                    "Only players can reset claims for a targeted container.",
                    NamedTextColor.RED
            ));
            return 0;
        }

        Optional<ResolvedPlayer> resolved = resolvePlayer(context);
        if (resolved.isEmpty()) {
            sender.sendMessage(Component.text("Resolve exactly one player.", NamedTextColor.RED));
            return 0;
        }

        ResolvedPlayer player = resolved.get();
        withTargetLootPoint(executor, lootPoint -> claimAdministrationService
                .resetClaim(player.id(), lootPoint.id())
                .whenComplete((deleted, exception) -> runOnMainThread(() -> {
                    if (exception != null) {
                        sender.sendMessage(Component.text("Unable to reset this claim.", NamedTextColor.RED));
                        return;
                    }
                    if (deleted == 0) {
                        sender.sendMessage(Component.text(
                                player.displayName() + " had not claimed this container.",
                                NamedTextColor.YELLOW
                        ));
                        return;
                    }
                    sender.sendMessage(Component.text(
                            "Reset " + player.displayName() + "'s claim for this container.",
                            NamedTextColor.GREEN
                    ));
                })));
        return Command.SINGLE_SUCCESS;
    }

    private Optional<ResolvedPlayer> resolvePlayer(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        PlayerProfileListResolver resolver = context.getArgument(
                PLAYER_ARGUMENT,
                PlayerProfileListResolver.class
        );
        Collection<com.destroystokyo.paper.profile.PlayerProfile> profiles = resolver.resolve(context.getSource());
        if (profiles.size() != 1) {
            return Optional.empty();
        }

        var profile = profiles.iterator().next();
        UUID id = profile.getId();
        if (id == null) {
            return Optional.empty();
        }
        return Optional.of(new ResolvedPlayer(id, profile.getName() == null ? id.toString() : profile.getName()));
    }

    private void withTargetLootPoint(Player player, Consumer<LootPoint> action) {
        lookupService.inspectTarget(player, TARGET_DISTANCE).whenComplete((inspection, exception) ->
                runOnMainThread(() -> {
                    if (exception != null) {
                        player.sendMessage(Component.text(
                                "Unable to read this loot point.",
                                NamedTextColor.RED
                        ));
                        return;
                    }
                    if (inspection.isEmpty()) {
                        player.sendMessage(SUPPORTED_CONTAINER_HELP);
                        return;
                    }

                    LootPointInspection target = inspection.get();
                    if (target.lootPoint().isPresent()) {
                        action.accept(target.lootPoint().get());
                    } else if (target.markerPresent()) {
                        player.sendMessage(Component.text(
                                "The PDC marker has no matching database record. Check the server log.",
                                NamedTextColor.RED
                        ));
                    } else {
                        player.sendMessage(Component.text(
                                "That container is not a registered loot point.",
                                NamedTextColor.YELLOW
                        ));
                    }
                })
        );
    }

    private void runOnMainThread(Runnable runnable) {
        plugin.getServer().getScheduler().runTask(plugin, runnable);
    }

    private record ResolvedPlayer(UUID id, String displayName) {
    }

    private CompletableFuture<Suggestions> suggestLootTables(
            CommandContext<CommandSourceStack> context,
            SuggestionsBuilder builder
    ) {
        String prefix = builder.getRemainingLowerCase();
        lootTableCatalog.suggestions(prefix).forEach(builder::suggest);
        return builder.buildFuture();
    }

    private static boolean hasAdminPermission(CommandSourceStack source) {
        return source.getSender().hasPermission(ADMIN_PERMISSION);
    }

    private static String valueOrDash(String value) {
        return value == null ? "-" : value;
    }

    private static String formatLocation(LootPointInspection inspection) {
        var location = inspection.location();
        return location.getWorld().getName() + " (" + location.getBlockX() + ", "
                + location.getBlockY() + ", " + location.getBlockZ() + ")";
    }
}
