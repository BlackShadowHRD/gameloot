package io.github.blackshadowhrd.poiloot.service;

import io.github.blackshadowhrd.poiloot.inventory.PrivateLootInventoryHolder;
import io.github.blackshadowhrd.poiloot.model.LootPoint;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class LootSessionService {

    private static final int PRIVATE_INVENTORY_SIZE = 27;

    private final PrivateInventoryService inventoryService;
    private final Map<SessionKey, LootSession> sessions = new HashMap<>();

    public LootSessionService(PrivateInventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    public Optional<Inventory> findInventory(UUID playerId, UUID lootPointId) {
        LootSession session = sessions.get(new SessionKey(playerId, lootPointId));
        return session == null ? Optional.empty() : Optional.of(session.inventory());
    }

    public Inventory createSession(LootPoint lootPoint, Player player, Collection<ItemStack> items) {
        SessionKey key = new SessionKey(player.getUniqueId(), lootPoint.id());
        LootSession existing = sessions.get(key);
        if (existing != null) {
            return existing.inventory();
        }

        Inventory inventory = inventoryService.createInventory(
                lootPoint,
                player,
                PRIVATE_INVENTORY_SIZE,
                items
        );
        sessions.put(key, new LootSession(inventory, false));
        return inventory;
    }

    public boolean markItemTaken(PrivateLootInventoryHolder holder) {
        SessionKey key = new SessionKey(holder.playerId(), holder.lootPointId());
        LootSession session = sessions.get(key);
        if (session == null || session.inventory() != holder.getInventory()) {
            return false;
        }
        if (session.itemTaken()) {
            return false;
        }

        sessions.put(key, new LootSession(session.inventory(), true));
        return true;
    }

    public void closeSession(PrivateLootInventoryHolder holder) {
        SessionKey key = new SessionKey(holder.playerId(), holder.lootPointId());
        LootSession session = sessions.get(key);
        if (session == null || session.inventory() != holder.getInventory() || !session.itemTaken()) {
            return;
        }

        sessions.remove(key);
        session.inventory().clear();
    }

    private record SessionKey(UUID playerId, UUID lootPointId) {
    }

    private record LootSession(Inventory inventory, boolean itemTaken) {
    }
}
