package io.github.blackshadowhrd.gameloot.validation;

public enum ValidationIssue {
    WORLD_UNAVAILABLE(ValidationStatus.UNVERIFIED, "World unavailable"),
    CHUNK_NOT_LOADED(ValidationStatus.UNVERIFIED, "Chunk not loaded"),
    TARGET_MISSING(ValidationStatus.INVALID, "Missing target"),
    TARGET_TYPE_MISMATCH(ValidationStatus.INVALID, "Target type mismatch"),
    PDC_MISSING(ValidationStatus.INVALID, "Missing PDC marker"),
    PDC_INVALID(ValidationStatus.INVALID, "Invalid PDC marker"),
    PDC_ID_MISMATCH(ValidationStatus.INVALID, "PDC UUID mismatch"),
    ENTITY_MISSING(ValidationStatus.INVALID, "Missing entity"),
    ENTITY_UUID_MISSING(ValidationStatus.INVALID, "Missing entity UUID"),
    ENTITY_LOCATION_MISMATCH(ValidationStatus.WARNING, "Entity location mismatch"),
    MISSING_LOOT_TABLE(ValidationStatus.INVALID, "Missing loot table"),
    INVALID_LOOT_METADATA(ValidationStatus.INVALID, "Invalid loot metadata"),
    MISSING_SHELF_REWARD(ValidationStatus.INVALID, "Missing shelf reward"),
    INVALID_SHELF_REWARD(ValidationStatus.INVALID, "Invalid shelf reward"),
    DUPLICATE_PHYSICAL_TARGET(ValidationStatus.INVALID, "Duplicate physical target");

    private final ValidationStatus status;
    private final String description;

    ValidationIssue(ValidationStatus status, String description) {
        this.status = status;
        this.description = description;
    }

    public ValidationStatus status() { return status; }
    public String description() { return description; }
}
