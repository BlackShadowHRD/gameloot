package io.github.blackshadowhrd.gameloot.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.github.blackshadowhrd.gameloot.model.LootPoint;
import io.github.blackshadowhrd.gameloot.service.ClaimAdministrationService;
import io.github.blackshadowhrd.gameloot.service.LootGenerationService;
import io.github.blackshadowhrd.gameloot.service.LootPointLookupService;
import io.github.blackshadowhrd.gameloot.service.LootPointRegistrar;
import io.github.blackshadowhrd.gameloot.target.LootPointInspection;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.PlayerProfileListResolver;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Collection;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public final class GameLootCommand {

    public static final String DESCRIPTION = "Manages GameLoot loot points";

    private static final String ADMIN_PERMISSION = "gameloot.admin";
    private static final String LOOT_TABLE_ARGUMENT = "loot-table";
    private static final String PLAYER_ARGUMENT = "player";
    private static final double TARGET_DISTANCE = 6;
    private static final Component SUPPORTED_CONTAINER_HELP =
            Component.text("Look at a supported container within 6 blocks.");

    private final Plugin plugin;
    private final LootPointRegistrar registrar;
    private final LootPointLookupService lookupService;
    private final LootGenerationService generationService;
    private final ClaimAdministrationService claimAdministrationService;

    public GameLootCommand(
            Plugin plugin,
            LootPointRegistrar registrar,
            LootPointLookupService lookupService,
            LootGenerationService generationService,
            ClaimAdministrationService claimAdministrationService
    ) {
        this.plugin = plugin;
        this.registrar = registrar;
        this.lookupService = lookupService;
        this.generationService = generationService;
        this.claimAdministrationService = claimAdministrationService;
    }

    public LiteralCommandNode<CommandSourceStack> createCommand() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("gameloot")
                .executes(this::help)
                .then(Commands.literal("version")
                        .executes(this::version))
                .then(Commands.literal("register")
                        .requires(GameLootCommand::hasAdminPermission)
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
                .then(Commands.literal("reset")
                        .requires(GameLootCommand::hasAdminPermission)
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
            sender.sendMessage(Component.text("/gameloot deregister"));
            sender.sendMessage(Component.text("/gameloot inspect"));
            sender.sendMessage(Component.text("/gameloot claims"));
            sender.sendMessage(Component.text("/gameloot reset container"));
            sender.sendMessage(Component.text("/gameloot reset player <player>"));
            sender.sendMessage(Component.text("/gameloot reset player <player> container"));
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
            case PERSISTENCE_FAILURE ->
                    sender.sendMessage(Component.text("Unable to persist this loot point.", NamedTextColor.RED));
        }
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
            sender.sendMessage(Component.text("Loot table: " + valueOrDash(
                    inspection.lootPoint().map(LootPoint::lootTable).map(NamespacedKey::asString).orElse(null)
            )));
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
            sender.sendMessage(Component.text("Loot table: " + lootPoint.lootTable()));
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
        Registry.LOOT_TABLES.keyStream()
                .map(NamespacedKey::asString)
                .filter(key -> key.toLowerCase(Locale.ROOT).startsWith(prefix))
                .sorted()
                .forEach(builder::suggest);
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
