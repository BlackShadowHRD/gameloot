package io.github.blackshadowhrd.gameloot.repository.model;

public record ShelfRewardItem(int slot, byte[] serializedItem) {
    public ShelfRewardItem {
        if (slot < 0 || slot > 2) throw new IllegalArgumentException("Shelf slot must be 0-2");
        serializedItem = serializedItem.clone();
    }

    @Override
    public byte[] serializedItem() { return serializedItem.clone(); }
}
