package io.github.blackshadowhrd.gameloot.model;

import java.util.List;

public record Page<T>(List<T> entries, int page, int totalPages, int totalEntries) {
    public Page {
        entries = List.copyOf(entries);
    }

    public static <T> Page<T> of(List<T> values, int requestedPage, int pageSize) {
        if (requestedPage < 1 || pageSize < 1) throw new IllegalArgumentException("Invalid page");
        int totalPages = Math.max(1, (values.size() + pageSize - 1) / pageSize);
        if (requestedPage > totalPages) throw new IllegalArgumentException("Page out of range");
        int from = Math.min((requestedPage - 1) * pageSize, values.size());
        int to = Math.min(from + pageSize, values.size());
        return new Page<>(values.subList(from, to), requestedPage, totalPages, values.size());
    }
}
