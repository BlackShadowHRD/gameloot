package io.github.blackshadowhrd.gameloot.validation;

import java.util.Optional;

public final class ValidationDecisions {
    private ValidationDecisions() { }

    public static Optional<ValidationIssue> world(boolean available) {
        return issueUnless(available, ValidationIssue.WORLD_UNAVAILABLE);
    }

    public static Optional<ValidationIssue> chunk(boolean loaded) {
        return issueUnless(loaded, ValidationIssue.CHUNK_NOT_LOADED);
    }

    public static Optional<ValidationIssue> target(boolean present) {
        return issueUnless(present, ValidationIssue.TARGET_MISSING);
    }

    public static Optional<ValidationIssue> targetType(boolean matches) {
        return issueUnless(matches, ValidationIssue.TARGET_TYPE_MISMATCH);
    }

    public static Optional<ValidationIssue> pdc(boolean present, boolean valid, boolean matches) {
        if (!present) return Optional.of(ValidationIssue.PDC_MISSING);
        if (!valid) return Optional.of(ValidationIssue.PDC_INVALID);
        return issueUnless(matches, ValidationIssue.PDC_ID_MISMATCH);
    }

    public static Optional<ValidationIssue> lootTable(boolean exists) {
        return issueUnless(exists, ValidationIssue.MISSING_LOOT_TABLE);
    }

    public static Optional<ValidationIssue> shelfReward(boolean present, boolean valid) {
        if (!present) return Optional.of(ValidationIssue.MISSING_SHELF_REWARD);
        return issueUnless(valid, ValidationIssue.INVALID_SHELF_REWARD);
    }

    public static Optional<ValidationIssue> entity(boolean found, boolean expectedChunkLoaded) {
        if (found) return Optional.empty();
        return Optional.of(expectedChunkLoaded ? ValidationIssue.ENTITY_MISSING : ValidationIssue.CHUNK_NOT_LOADED);
    }

    private static Optional<ValidationIssue> issueUnless(boolean condition, ValidationIssue issue) {
        return condition ? Optional.empty() : Optional.of(issue);
    }
}
