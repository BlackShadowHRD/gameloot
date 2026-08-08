package io.github.blackshadowhrd.gameloot.listener;

import io.github.blackshadowhrd.gameloot.service.LootPointProtectionPolicy;
import io.github.blackshadowhrd.gameloot.service.LootPointProtectionService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.event.vehicle.VehicleEntityCollisionEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class LootPointProtectionListener implements Listener {

    private static final long MESSAGE_COOLDOWN_MILLIS = 2_000;
    private static final Component PROTECTED_MESSAGE = Component.text(
            "This is a protected GameLoot container.", NamedTextColor.YELLOW);

    private final LootPointProtectionService protectionService;
    private final Map<UUID, Long> lastBreakMessage = new HashMap<>();

    public LootPointProtectionListener(LootPointProtectionService protectionService) {
        this.protectionService = protectionService;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!protectionService.isProtected(event.getBlock())) return;
        event.setCancelled(true);
        sendBreakMessage(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplosion(EntityExplodeEvent event) {
        removeProtected(event.blockList());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplosion(BlockExplodeEvent event) {
        removeProtected(event.blockList());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (LootPointProtectionPolicy.containsProtected(event.getBlocks(), protectionService::isProtected)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (LootPointProtectionPolicy.containsProtected(event.getBlocks(), protectionService::isProtected)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryMove(InventoryMoveItemEvent event) {
        boolean sourceProtected = protectionService.isProtected(event.getSource());
        boolean destinationProtected = protectionService.isProtected(event.getDestination());
        if (LootPointProtectionPolicy.blocksInventoryTransfer(sourceProtected, destinationProtected)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        if (protectionService.isProtected(event.getBlock())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockForm(BlockFormEvent event) {
        if (protectionService.isProtected(event.getBlock())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (protectionService.isProtected(event.getEntity())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (protectionService.isProtected(event.getBlock())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVehicleDestroy(VehicleDestroyEvent event) {
        if (protectionService.isProtected(event.getVehicle())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVehicleCollision(VehicleEntityCollisionEvent event) {
        if (protectionService.isProtected(event.getVehicle())
                || protectionService.isProtected(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityPortal(EntityPortalEvent event) {
        if (protectionService.isProtected(event.getEntity())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onVehicleMove(VehicleMoveEvent event) {
        if (!protectionService.isProtected(event.getVehicle()) || samePosition(event)) return;
        event.getVehicle().setVelocity(new Vector());
        event.getVehicle().teleport(event.getFrom());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        lastBreakMessage.remove(event.getPlayer().getUniqueId());
    }

    private void removeProtected(List<Block> blocks) {
        blocks.removeIf(protectionService::isProtected);
    }

    private void sendBreakMessage(Player player) {
        long now = System.currentTimeMillis();
        Long previous = lastBreakMessage.put(player.getUniqueId(), now);
        if (previous == null || now - previous >= MESSAGE_COOLDOWN_MILLIS) {
            player.sendMessage(PROTECTED_MESSAGE);
        }
    }

    private boolean samePosition(VehicleMoveEvent event) {
        return event.getFrom().getWorld().equals(event.getTo().getWorld())
                && event.getFrom().getX() == event.getTo().getX()
                && event.getFrom().getY() == event.getTo().getY()
                && event.getFrom().getZ() == event.getTo().getZ();
    }
}
