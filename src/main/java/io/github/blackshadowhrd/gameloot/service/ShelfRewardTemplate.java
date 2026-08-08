package io.github.blackshadowhrd.gameloot.service;

import io.github.blackshadowhrd.gameloot.repository.model.ShelfRewardItem;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class ShelfRewardTemplate {

    private ShelfRewardTemplate() {
    }

    public static Optional<List<ShelfRewardItem>> capture(ItemStack[] contents) {
        List<ShelfRewardItem> rewards = new ArrayList<>();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (item != null && !item.isEmpty()) {
                rewards.add(new ShelfRewardItem(slot, item.clone().serializeAsBytes()));
            }
        }
        return rewards.isEmpty() ? Optional.empty() : Optional.of(List.copyOf(rewards));
    }

    public static List<ItemStack> restore(List<ShelfRewardItem> rewards) {
        return rewards.stream()
                .map(reward -> ItemStack.deserializeBytes(reward.serializedItem()).clone())
                .toList();
    }
}
