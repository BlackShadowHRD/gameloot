package io.github.blackshadowhrd.poiloot.listener;

import io.github.blackshadowhrd.poiloot.inventory.PrivateLootInventoryHolder;
import io.github.blackshadowhrd.poiloot.service.InMemoryClaimService;
import io.github.blackshadowhrd.poiloot.service.LootSessionService;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

public final class PrivateLootInventoryListener implements Listener {

    private final InMemoryClaimService claimService;
    private final LootSessionService sessionService;

    public PrivateLootInventoryListener(
            InMemoryClaimService claimService,
            LootSessionService sessionService
    ) {
        this.claimService = claimService;
        this.sessionService = sessionService;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof PrivateLootInventoryHolder holder)) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)
                || !player.getUniqueId().equals(holder.playerId())) {
            event.setCancelled(true);
            return;
        }

        int rawSlot = event.getRawSlot();
        int topSize = event.getView().getTopInventory().getSize();
        if (rawSlot >= 0 && rawSlot < topSize) {
            event.setCancelled(true);
            takeLoot(event, player, holder);
            return;
        }

        if (event.isShiftClick() || event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof PrivateLootInventoryHolder holder)) {
            return;
        }

        int topSize = event.getView().getTopInventory().getSize();
        if (!event.getWhoClicked().getUniqueId().equals(holder.playerId())
                || event.getRawSlots().stream().anyMatch(slot -> slot < topSize)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof PrivateLootInventoryHolder holder
                && event.getPlayer().getUniqueId().equals(holder.playerId())) {
            sessionService.closeSession(holder);
        }
    }

    private void takeLoot(
            InventoryClickEvent event,
            Player player,
            PrivateLootInventoryHolder holder
    ) {
        ItemStack current = event.getCurrentItem();
        if (isEmpty(current)) {
            return;
        }

        boolean moved;
        if (event.isShiftClick()) {
            moved = moveToPlayerInventory(event, player, current);
        } else {
            moved = moveToCursor(event, current);
        }

        if (moved && sessionService.markItemTaken(holder)) {
            claimService.markClaimed(holder.playerId(), holder.lootPointId());
        }
    }

    private boolean moveToPlayerInventory(InventoryClickEvent event, Player player, ItemStack current) {
        int originalAmount = current.getAmount();
        Map<Integer, ItemStack> remainingItems = player.getInventory().addItem(current.clone());
        int remainingAmount = remainingItems.values().stream()
                .mapToInt(ItemStack::getAmount)
                .sum();
        if (remainingAmount == originalAmount) {
            return false;
        }

        event.setCurrentItem(withAmount(current, remainingAmount));
        return true;
    }

    private boolean moveToCursor(InventoryClickEvent event, ItemStack current) {
        if (!isEmpty(event.getCursor())
                || (event.getClick() != ClickType.LEFT && event.getClick() != ClickType.RIGHT)) {
            return false;
        }

        int amount = event.getClick() == ClickType.RIGHT
                ? (current.getAmount() + 1) / 2
                : current.getAmount();
        event.getView().setCursor(withAmount(current, amount));
        event.setCurrentItem(withAmount(current, current.getAmount() - amount));
        return true;
    }

    private ItemStack withAmount(ItemStack source, int amount) {
        if (amount <= 0) {
            return null;
        }

        ItemStack result = source.clone();
        result.setAmount(amount);
        return result;
    }

    private boolean isEmpty(ItemStack item) {
        return item == null || item.getType() == Material.AIR || item.getAmount() <= 0;
    }
}
