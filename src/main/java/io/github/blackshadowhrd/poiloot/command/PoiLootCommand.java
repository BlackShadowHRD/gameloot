package io.github.blackshadowhrd.poiloot.command;

import io.github.blackshadowhrd.poiloot.loot.LootPointRegistrar;
import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Locale;

public final class PoiLootCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("version", "inspect", "register", "deregister");

    private final Plugin plugin;
    private final LootPointRegistrar registrar;

    public PoiLootCommand(Plugin plugin) {
        this.plugin = plugin;
        registrar = new LootPointRegistrar(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("version")) {
            sender.sendMessage(Component.text(
                    plugin.getPluginMeta().getName() + " " + plugin.getPluginMeta().getVersion()
            ));
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("inspect")) {
            return inspect(sender);
        }

        if (args.length == 1 && (args[0].equalsIgnoreCase("deregister")
                || args[0].equalsIgnoreCase("de-register"))) {
            return deregister(sender);
        }

        if (args.length != 2 || !args[0].equalsIgnoreCase("register")) {
            return false;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can register loot points."));
            return true;
        }

        NamespacedKey lootTable = NamespacedKey.fromString(args[1]);
        if (lootTable == null) {
            sender.sendMessage(Component.text("Invalid loot-table key: " + args[1]));
            return true;
        }

        switch (registrar.register(player, lootTable)) {
            case REGISTERED -> sender.sendMessage(Component.text("Loot point registered with " + lootTable));
            case ALREADY_REGISTERED -> sender.sendMessage(Component.text("That container is already a loot point."));
            case INVALID_TARGET -> sender.sendMessage(Component.text(
                    "Look at a chest, barrel, or chest minecart within 6 blocks."
            ));
        }
        return true;
    }

    private boolean deregister(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can deregister loot points."));
            return true;
        }

        switch (registrar.deregister(player)) {
            case DEREGISTERED -> sender.sendMessage(Component.text("Loot point deregistered."));
            case NOT_REGISTERED -> sender.sendMessage(Component.text("That container is not a registered loot point."));
            case INVALID_TARGET -> sender.sendMessage(Component.text(
                    "Look at a chest, barrel, or chest minecart within 6 blocks."
            ));
        }
        return true;
    }

    private boolean inspect(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can inspect loot points."));
            return true;
        }

        registrar.inspect(player).ifPresentOrElse(inspection -> {
            sender.sendMessage(Component.text("Registered: " + (inspection.registered() ? "Yes" : "No")));
            sender.sendMessage(Component.text("Id: " + valueOrDash(inspection.id())));
            sender.sendMessage(Component.text("Type: " + inspection.type()));
            sender.sendMessage(Component.text("Loot table: " + valueOrDash(inspection.lootTable())));
            sender.sendMessage(Component.text("Location: " + inspection.location()));
        }, () -> sender.sendMessage(Component.text(
                "Look at a chest, barrel, or chest minecart within 6 blocks."
        )));
        return true;
    }

    private String valueOrDash(String value) {
        return value == null ? "-" : value;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return matching(SUBCOMMANDS, args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("register")) {
            List<String> lootTables = Registry.LOOT_TABLES.keyStream()
                    .map(NamespacedKey::asString)
                    .sorted()
                    .toList();
            return matching(lootTables, args[1]);
        }
        return List.of();
    }

    private List<String> matching(List<String> candidates, String input) {
        String prefix = input.toLowerCase(Locale.ROOT);
        return candidates.stream()
                .filter(candidate -> candidate.startsWith(prefix))
                .toList();
    }
}
