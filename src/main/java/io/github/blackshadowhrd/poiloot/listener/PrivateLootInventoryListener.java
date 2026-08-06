package io.github.blackshadowhrd.poiloot.listener;

import io.github.blackshadowhrd.poiloot.inventory.PrivateLootInventoryHolder;
import io.github.blackshadowhrd.poiloot.service.ClaimService;
import io.github.blackshadowhrd.poiloot.service.LootSessionService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
import org.bukkit.plugin.Plugin;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class PrivateLootInventoryListener implements Listener {

    private final ClaimService claimService;
    private final LootSessionService sessionService;
    private final Plugin plugin;
    private final Set<PrivateLootInventoryHolder> pendingClaims = new HashSet<>();

    public PrivateLootInventoryListener(
            Plugin plugin,
            ClaimService claimService,
            LootSessionService sessionService
    ) {
        this.plugin = plugin;
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

        if (sessionService.hasTakenItem(holder)) {
            transferImmediately(event, player, current);
            return;
        }
        if (event.isShiftClick()) {
            if (!canAcceptAny(player, current)) {
                return;
            }
        } else if (!isEmpty(event.getCursor())
                || (event.getClick() != ClickType.LEFT && event.getClick() != ClickType.RIGHT)) {
            return;
        }
        if (!pendingClaims.add(holder)) {
            return;
        }

        int slot = event.getRawSlot();
        ClickType click = event.getClick();
        boolean shiftClick = event.isShiftClick();
        claimService.markClaimed(holder.playerId(), holder.lootPointId()).whenComplete((persisted, exception) ->
                plugin.getServer().getScheduler().runTask(plugin, () -> completeFirstTransfer(
                        player,
                        holder,
                        slot,
                        click,
                        shiftClick,
                        persisted != null && persisted && exception == null
                ))
        );
    }

    private void completeFirstTransfer(
            Player player,
            PrivateLootInventoryHolder holder,
            int slot,
            ClickType click,
            boolean shiftClick,
            boolean persisted
    ) {
        pendingClaims.remove(holder);
        if (!persisted) {
            player.sendMessage(Component.text(
                    "Unable to save your loot claim. Access has been blocked to protect your loot.",
                    NamedTextColor.RED
            ));
            if (player.getOpenInventory().getTopInventory() == holder.getInventory()) {
                player.closeInventory();
            }
            return;
        }

        ItemStack current = holder.getInventory().getItem(slot);
        if (isEmpty(current)) {
            player.sendMessage(Component.text(
                    "Your claim was saved, but the selected loot item was no longer available.",
                    NamedTextColor.RED
            ));
            sessionService.markItemTaken(holder);
            discardIfClosed(player, holder);
            return;
        }

        if (shiftClick) {
            moveFromSessionToPlayer(player, holder, slot, current);
        } else {
            int amount = click == ClickType.RIGHT ? (current.getAmount() + 1) / 2 : current.getAmount();
            ItemStack taken = withAmount(current, amount);
            holder.getInventory().setItem(slot, withAmount(current, current.getAmount() - amount));
            deliverToCursorOrInventory(player, holder, taken);
        }
        sessionService.markItemTaken(holder);
        discardIfClosed(player, holder);
    }

    private void transferImmediately(InventoryClickEvent event, Player player, ItemStack current) {
        if (event.isShiftClick()) {
            moveToPlayerInventory(event, player, current);
        } else {
            moveToCursor(event, current);
        }
    }

    private void moveFromSessionToPlayer(
            Player player,
            PrivateLootInventoryHolder holder,
            int slot,
            ItemStack current
    ) {
        Map<Integer, ItemStack> remaining = player.getInventory().addItem(current.clone());
        holder.getInventory().setItem(slot, null);
        remaining.values().forEach(item ->
                player.getWorld().dropItemNaturally(player.getLocation(), item)
        );
    }

    private void deliverToCursorOrInventory(
            Player player,
            PrivateLootInventoryHolder holder,
            ItemStack item
    ) {
        if (player.getOpenInventory().getTopInventory() == holder.getInventory()
                && isEmpty(player.getItemOnCursor())) {
            player.setItemOnCursor(item);
            return;
        }

        player.getInventory().addItem(item).values().forEach(remaining ->
                player.getWorld().dropItemNaturally(player.getLocation(), remaining)
        );
    }

    private void discardIfClosed(Player player, PrivateLootInventoryHolder holder) {
        if (player.getOpenInventory().getTopInventory() != holder.getInventory()) {
            sessionService.discardSession(holder);
        }
    }

    private boolean canAcceptAny(Player player, ItemStack item) {
        for (ItemStack stored : player.getInventory().getStorageContents()) {
            if (isEmpty(stored)
                    || (stored.isSimilar(item) && stored.getAmount() < stored.getMaxStackSize())) {
                return true;
            }
        }
        return false;
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
