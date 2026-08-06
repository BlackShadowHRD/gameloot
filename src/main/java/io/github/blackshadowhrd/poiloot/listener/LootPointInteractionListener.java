package io.github.blackshadowhrd.poiloot.listener;

import io.github.blackshadowhrd.poiloot.model.LootPoint;
import io.github.blackshadowhrd.poiloot.service.InMemoryClaimService;
import io.github.blackshadowhrd.poiloot.service.LootGenerationService;
import io.github.blackshadowhrd.poiloot.service.LootPointLookupService;
import io.github.blackshadowhrd.poiloot.service.LootSessionService;
import io.github.blackshadowhrd.poiloot.service.PrivateInventoryService;
import io.github.blackshadowhrd.poiloot.target.LootPointTarget;
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

    private final LootPointLookupService lookupService;
    private final LootGenerationService generationService;
    private final PrivateInventoryService inventoryService;
    private final InMemoryClaimService claimService;
    private final LootSessionService sessionService;
    private final Logger logger;

    public LootPointInteractionListener(
            Plugin plugin,
            LootPointLookupService lookupService,
            LootGenerationService generationService,
            PrivateInventoryService inventoryService,
            InMemoryClaimService claimService,
            LootSessionService sessionService
    ) {
        this.lookupService = lookupService;
        this.generationService = generationService;
        this.inventoryService = inventoryService;
        this.claimService = claimService;
        this.sessionService = sessionService;
        logger = plugin.getLogger();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockInteraction(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        lookupService.findTarget(event.getClickedBlock()).ifPresent(target -> {
            event.setCancelled(true);
            openPrivateLoot(event.getPlayer(), target);
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityInteraction(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        lookupService.findTarget(event.getRightClicked()).ifPresent(target -> {
            event.setCancelled(true);
            openPrivateLoot(event.getPlayer(), target);
        });
    }

    private void openPrivateLoot(Player player, LootPointTarget target) {
        LootPoint lootPoint = target.lootPoint();
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

    private String formatLocation(Location location) {
        return location.getWorld().getName() + " (" + location.getBlockX() + ", "
                + location.getBlockY() + ", " + location.getBlockZ() + ")";
    }
}
