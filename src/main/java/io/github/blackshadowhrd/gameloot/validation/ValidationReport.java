package io.github.blackshadowhrd.gameloot.validation;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public record ValidationReport(
        List<LootPointValidationResult> lootPoints,
        DatabaseIntegrityReport databaseIntegrity
) {
    public ValidationReport {
        lootPoints = List.copyOf(lootPoints);
    }

    public long count(ValidationStatus status) {
        return lootPoints.stream().filter(result -> result.status() == status).count();
    }

    public Map<ValidationIssue, Long> issueCounts() {
        Map<ValidationIssue, Long> counts = new EnumMap<>(ValidationIssue.class);
        lootPoints.stream().flatMap(result -> result.issues().stream())
                .forEach(issue -> counts.merge(issue, 1L, Long::sum));
        return Map.copyOf(counts);
    }
}
