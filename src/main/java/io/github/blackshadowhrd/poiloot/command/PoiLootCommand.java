package io.github.blackshadowhrd.poiloot.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.github.blackshadowhrd.poiloot.model.LootPoint;
import io.github.blackshadowhrd.poiloot.service.LootGenerationService;
import io.github.blackshadowhrd.poiloot.service.LootPointLookupService;
import io.github.blackshadowhrd.poiloot.service.LootPointRegistrar;
import io.github.blackshadowhrd.poiloot.target.LootPointInspection;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public final class PoiLootCommand {

    public static final String DESCRIPTION = "Manages POILoot loot points";

    private static final String ADMIN_PERMISSION = "poiloot.admin";
    private static final String LOOT_TABLE_ARGUMENT = "loot-table";
    private static final double TARGET_DISTANCE = 6;
    private static final Component SUPPORTED_CONTAINER_HELP =
            Component.text("Look at a supported container within 6 blocks.");

    private final Plugin plugin;
    private final LootPointRegistrar registrar;
    private final LootPointLookupService lookupService;
    private final LootGenerationService generationService;

    public PoiLootCommand(
            Plugin plugin,
            LootPointRegistrar registrar,
            LootPointLookupService lookupService,
            LootGenerationService generationService
    ) {
        this.plugin = plugin;
        this.registrar = registrar;
        this.lookupService = lookupService;
        this.generationService = generationService;
    }

    public LiteralCommandNode<CommandSourceStack> createCommand() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("poiloot")
                .executes(this::help)
                .then(Commands.literal("version")
                        .executes(this::version))
                .then(Commands.literal("register")
                        .requires(PoiLootCommand::hasAdminPermission)
                        .then(Commands.argument(LOOT_TABLE_ARGUMENT, ArgumentTypes.namespacedKey())
                                .suggests(this::suggestLootTables)
                                .executes(this::register)))
                .then(Commands.literal("deregister")
                        .requires(PoiLootCommand::hasAdminPermission)
                        .executes(this::deregister))
                .then(Commands.literal("de-register")
                        .requires(PoiLootCommand::hasAdminPermission)
                        .executes(this::deregister))
                .then(Commands.literal("inspect")
                        .requires(PoiLootCommand::hasAdminPermission)
                        .executes(this::inspect));

        return root.build();
    }

    private int help(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();

        sender.sendMessage(Component.text("POILoot commands:"));
        sender.sendMessage(Component.text("/poiloot version"));

        if (sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage(Component.text("/poiloot register <loot-table>"));
            sender.sendMessage(Component.text("/poiloot deregister"));
            sender.sendMessage(Component.text("/poiloot inspect"));
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
        switch (registrar.register(player, lootTable)) {
            case REGISTERED -> {
                sender.sendMessage(Component.text("Loot point registered with " + lootTable, NamedTextColor.GREEN));
                return Command.SINGLE_SUCCESS;
            }
            case ALREADY_REGISTERED ->
                    sender.sendMessage(Component.text("That container is already a loot point.", NamedTextColor.YELLOW));
            case INVALID_TARGET -> sender.sendMessage(SUPPORTED_CONTAINER_HELP);
        }
        return 0;
    }

    private int deregister(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can deregister loot points."));
            return 0;
        }

        switch (registrar.deregister(player)) {
            case DEREGISTERED -> {
                sender.sendMessage(Component.text("Loot point deregistered."));
                return Command.SINGLE_SUCCESS;
            }
            case NOT_REGISTERED ->
                    sender.sendMessage(Component.text("That container is not a registered loot point."));
            case INVALID_TARGET -> sender.sendMessage(SUPPORTED_CONTAINER_HELP);
        }
        return 0;
    }

    private int inspect(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can inspect loot points."));
            return 0;
        }

        return lookupService.inspectTarget(player, TARGET_DISTANCE).map(inspection -> {
            sender.sendMessage(Component.text("Registered: " + (inspection.lootPoint().isPresent() ? "Yes" : "No")));
            sender.sendMessage(Component.text("Id: " + valueOrDash(
                    inspection.lootPoint().map(LootPoint::id).map(Object::toString).orElse(null)
            )));
            sender.sendMessage(Component.text("Type: " + inspection.displayType()));
            sender.sendMessage(Component.text("Loot table: " + valueOrDash(
                    inspection.lootPoint().map(LootPoint::lootTable).map(NamespacedKey::asString).orElse(null)
            )));
            sender.sendMessage(Component.text("Location: " + formatLocation(inspection)));
            return Command.SINGLE_SUCCESS;
        }).orElseGet(() -> {
            sender.sendMessage(SUPPORTED_CONTAINER_HELP);
            return 0;
        });
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
