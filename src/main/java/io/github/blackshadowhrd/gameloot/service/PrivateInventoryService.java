package io.github.blackshadowhrd.gameloot.service;

import io.github.blackshadowhrd.gameloot.model.LootPoint;
import io.github.blackshadowhrd.gameloot.inventory.PrivateLootInventoryHolder;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;

public final class PrivateInventoryService {

    private final Server server;

    public PrivateInventoryService(Server server) {
        this.server = server;
    }

    public Inventory createInventory(
            LootPoint lootPoint,
            Player player,
            int size,
            Collection<ItemStack> items
    ) {
        Objects.requireNonNull(lootPoint, "lootPoint");
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(items, "items");

        PrivateLootInventoryHolder holder = new PrivateLootInventoryHolder(
                server,
                player.getUniqueId(),
                lootPoint.id(),
                size
        );
        Inventory inventory = holder.getInventory();
        ItemStack[] contents = items.stream()
                .map(ItemStack::clone)
                .toArray(ItemStack[]::new);
        Map<Integer, ItemStack> remaining = inventory.addItem(contents);
        if (!remaining.isEmpty()) {
            throw new IllegalArgumentException("Items do not fit in the requested private inventory");
        }
        return inventory;
    }

    public void openInventory(Player player, Inventory inventory) {
        player.openInventory(inventory);
    }
}
