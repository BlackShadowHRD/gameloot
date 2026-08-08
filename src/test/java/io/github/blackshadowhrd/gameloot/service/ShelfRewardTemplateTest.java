package io.github.blackshadowhrd.gameloot.service;

import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ShelfRewardTemplateTest {

    @Test
    void rejectsEmptyShelf() {
        assertTrue(ShelfRewardTemplate.capture(new ItemStack[3]).isEmpty());
    }

}
