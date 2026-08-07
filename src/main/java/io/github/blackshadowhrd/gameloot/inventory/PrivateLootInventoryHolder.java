package io.github.blackshadowhrd.gameloot.inventory;

import net.kyori.adventure.text.Component;
import org.bukkit.Server;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.Objects;
import java.util.UUID;

public final class PrivateLootInventoryHolder implements InventoryHolder {

    private final UUID playerId;
    private final UUID lootPointId;
    private final Inventory inventory;

    public PrivateLootInventoryHolder(Server server, UUID playerId, UUID lootPointId, int size) {
        this.playerId = Objects.requireNonNull(playerId, "playerId");
        this.lootPointId = Objects.requireNonNull(lootPointId, "lootPointId");
        inventory = server.createInventory(this, size, Component.text("GameLoot"));
    }

    public UUID playerId() {
        return playerId;
    }

    public UUID lootPointId() {
        return lootPointId;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
