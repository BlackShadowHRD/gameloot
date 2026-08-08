package io.github.blackshadowhrd.gameloot.listener;

import io.github.blackshadowhrd.gameloot.model.LootPoint;
import io.github.blackshadowhrd.gameloot.model.LootPointType;
import io.github.blackshadowhrd.gameloot.service.ClaimService;
import io.github.blackshadowhrd.gameloot.service.LootGenerationService;
import io.github.blackshadowhrd.gameloot.service.LootPointProtectionPolicy;
import io.github.blackshadowhrd.gameloot.service.LootPointProtectionService;
import io.github.blackshadowhrd.gameloot.service.LootSessionService;
import io.github.blackshadowhrd.gameloot.service.PrivateInventoryService;
import io.github.blackshadowhrd.gameloot.service.ShelfRewardService;
import io.github.blackshadowhrd.gameloot.target.LootPointTarget;
import io.github.blackshadowhrd.gameloot.target.LootPointResolution;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class LootPointInteractionListener implements Listener {

    private final LootPointProtectionService protectionService;
    private final LootGenerationService generationService;
    private final PrivateInventoryService inventoryService;
    private final ClaimService claimService;
    private final LootSessionService sessionService;
    private final Logger logger;
    private final Plugin plugin;
    private final ShelfRewardService shelfRewardService;

    public LootPointInteractionListener(
            Plugin plugin,
            LootPointProtectionService protectionService,
            LootGenerationService generationService,
            PrivateInventoryService inventoryService,
            ClaimService claimService,
            LootSessionService sessionService,
            ShelfRewardService shelfRewardService
    ) {
        this.plugin = plugin;
        this.protectionService = protectionService;
        this.generationService = generationService;
        this.inventoryService = inventoryService;
        this.claimService = claimService;
        this.sessionService = sessionService;
        this.shelfRewardService = shelfRewardService;
        logger = plugin.getLogger();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockInteraction(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (!LootPointProtectionPolicy.handlesLootInteraction(event.getPlayer().getGameMode())) return;

        LootPointResolution resolution = protectionService.resolve(event.getClickedBlock());
        if (resolution.marked()) {
            event.setCancelled(true);
            openResolvedTarget(event.getPlayer(), resolution);
        } else if (protectionService.isProtectedAccess(event.getClickedBlock())) {
            // The other half of a double chest may carry the marker.
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityInteraction(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (!LootPointProtectionPolicy.handlesLootInteraction(event.getPlayer().getGameMode())) return;

        LootPointResolution resolution = protectionService.resolve(event.getRightClicked());
        if (resolution.marked()) {
            event.setCancelled(true);
            openResolvedTarget(event.getPlayer(), resolution);
        }
    }

    private void openResolvedTarget(Player player, LootPointResolution resolution) {
        resolution.target().whenComplete((target, exception) -> plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (exception != null || target.isEmpty()) {
                player.sendMessage(Component.text(
                        "Loot point data is unavailable. Contact an administrator.",
                        NamedTextColor.RED
                ));
                return;
            }
            openPrivateLoot(player, target.get());
        }));
    }

    private void openPrivateLoot(Player player, LootPointTarget target) {
        LootPoint lootPoint = target.lootPoint();
        if (lootPoint.type() == LootPointType.SHELF) {
            claimShelf(player, lootPoint);
            return;
        }
        if (claimService.isClaimed(player.getUniqueId(), lootPoint.id())) {
            player.sendMessage(Component.text("You have already claimed this loot point.", NamedTextColor.YELLOW));
            return;
        }

        Inventory existingInventory = sessionService.findInventory(player.getUniqueId(), lootPoint.id())
                .orElse(null);
        if (existingInventory != null) {
            inventoryService.openInventory(player, existingInventory);
            return;
        }

        if (generationService.resolveLootTable(lootPoint.lootTable()).isEmpty()) {
            player.sendMessage(Component.text("Unknown loot table: " + lootPoint.lootTable(), NamedTextColor.RED));
            logger.warning("Missing loot table '" + lootPoint.lootTable() + "' for loot point "
                    + lootPoint.id() + " at " + formatLocation(target.location()));
            return;
        }

        try {
            List<ItemStack> items = generationService.generateLoot(lootPoint, player, target.location());
            Inventory inventory = sessionService.createSession(lootPoint, player, items);
            inventoryService.openInventory(player, inventory);
        } catch (RuntimeException exception) {
            player.sendMessage(Component.text("Unable to generate loot for this loot point.", NamedTextColor.RED));
            logger.log(
                    Level.SEVERE,
                    "Failed to generate loot for loot point " + lootPoint.id() + " at "
                            + formatLocation(target.location()),
                    exception
            );
        }
    }

    private void claimShelf(Player player, LootPoint lootPoint) {
        try {
            shelfRewardService.claim(player, lootPoint).whenComplete((result, exception) ->
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        if (exception != null) {
                            player.sendMessage(Component.text("Unable to claim this shelf reward.", NamedTextColor.RED));
                            return;
                        }
                        switch (result) {
                            case CLAIMED -> player.sendMessage(Component.text("Shelf reward claimed.", NamedTextColor.GREEN));
                            case ALREADY_CLAIMED -> player.sendMessage(Component.text(
                                    "You have already claimed this loot point.", NamedTextColor.YELLOW));
                            case INSUFFICIENT_SPACE -> player.sendMessage(Component.text(
                                    "Make enough inventory space for the complete shelf reward and try again.",
                                    NamedTextColor.YELLOW));
                            case MISSING_REWARD -> player.sendMessage(Component.text(
                                    "This shelf reward is unavailable. Contact an administrator.", NamedTextColor.RED));
                            case PERSISTENCE_FAILURE -> player.sendMessage(Component.text(
                                    "Unable to save your shelf claim. No items were transferred.", NamedTextColor.RED));
                        }
                    }));
        } catch (RuntimeException exception) {
            logger.log(Level.SEVERE, "Failed to read shelf reward for loot point " + lootPoint.id(), exception);
            player.sendMessage(Component.text("This shelf reward is unavailable. Contact an administrator.",
                    NamedTextColor.RED));
        }
    }

    private String formatLocation(Location location) {
        return location.getWorld().getName() + " (" + location.getBlockX() + ", "
                + location.getBlockY() + ", " + location.getBlockZ() + ")";
    }
}
