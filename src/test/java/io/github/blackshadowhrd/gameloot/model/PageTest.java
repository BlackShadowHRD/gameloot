package io.github.blackshadowhrd.gameloot.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PageTest {
    @Test
    void paginatesBoundariesAndEmptyLists() {
        List<Integer> values = java.util.stream.IntStream.rangeClosed(1, 21).boxed().toList();
        Page<Integer> first = Page.of(values, 1, 10);
        Page<Integer> last = Page.of(values, 3, 10);
        assertEquals(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10), first.entries());
        assertEquals(List.of(21), last.entries());
        assertEquals(3, first.totalPages());
        Page<Integer> empty = Page.of(List.of(), 1, 10);
        assertTrue(empty.entries().isEmpty());
        assertEquals(1, empty.totalPages());
        assertThrows(IllegalArgumentException.class, () -> Page.of(values, 4, 10));
    }
}
