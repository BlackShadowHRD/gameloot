package io.github.blackshadowhrd.gameloot.validation;

import io.github.blackshadowhrd.gameloot.repository.model.LootPointRecord;

import java.util.List;

public record LootPointValidationResult(LootPointRecord lootPoint, List<ValidationIssue> issues) {

    public LootPointValidationResult {
        issues = List.copyOf(issues);
    }

    public ValidationStatus status() {
        if (issues.stream().anyMatch(issue -> issue.status() == ValidationStatus.INVALID)) {
            return ValidationStatus.INVALID;
        }
        if (issues.stream().anyMatch(issue -> issue.status() == ValidationStatus.UNVERIFIED)) {
            return ValidationStatus.UNVERIFIED;
        }
        if (!issues.isEmpty()) return ValidationStatus.WARNING;
        return ValidationStatus.VALID;
    }
}
