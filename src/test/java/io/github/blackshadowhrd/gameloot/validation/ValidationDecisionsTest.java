package io.github.blackshadowhrd.gameloot.validation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValidationDecisionsTest {

    @Test
    void validTargetProducesNoIssues() {
        assertTrue(ValidationDecisions.world(true).isEmpty());
        assertTrue(ValidationDecisions.chunk(true).isEmpty());
        assertTrue(ValidationDecisions.target(true).isEmpty());
        assertTrue(ValidationDecisions.targetType(true).isEmpty());
        assertTrue(ValidationDecisions.pdc(true, true, true).isEmpty());
        assertTrue(ValidationDecisions.lootTable(true).isEmpty());
        assertTrue(ValidationDecisions.shelfReward(true, true).isEmpty());
    }

    @Test
    void unavailableWorldAndChunkAreUnverified() {
        assertIssue(ValidationIssue.WORLD_UNAVAILABLE, ValidationDecisions.world(false));
        assertIssue(ValidationIssue.CHUNK_NOT_LOADED, ValidationDecisions.chunk(false));
        assertEquals(ValidationStatus.UNVERIFIED,
                new LootPointValidationResult(null, List.of(ValidationIssue.CHUNK_NOT_LOADED)).status());
    }

    @Test
    void inspectableTargetFailuresAreInvalid() {
        assertIssue(ValidationIssue.TARGET_MISSING, ValidationDecisions.target(false));
        assertIssue(ValidationIssue.TARGET_TYPE_MISMATCH, ValidationDecisions.targetType(false));
        assertIssue(ValidationIssue.PDC_MISSING, ValidationDecisions.pdc(false, false, false));
        assertIssue(ValidationIssue.PDC_INVALID, ValidationDecisions.pdc(true, false, false));
        assertIssue(ValidationIssue.PDC_ID_MISMATCH, ValidationDecisions.pdc(true, true, false));
        assertIssue(ValidationIssue.MISSING_LOOT_TABLE, ValidationDecisions.lootTable(false));
    }

    @Test
    void shelfRewardDistinguishesValidMissingAndCorruptData() {
        assertTrue(ValidationDecisions.shelfReward(true, true).isEmpty());
        assertIssue(ValidationIssue.MISSING_SHELF_REWARD,
                ValidationDecisions.shelfReward(false, false));
        assertIssue(ValidationIssue.INVALID_SHELF_REWARD,
                ValidationDecisions.shelfReward(true, false));
    }

    @Test
    void chestMinecartDistinguishesUnavailableFromDefinitelyMissing() {
        assertTrue(ValidationDecisions.entity(true, true).isEmpty());
        assertIssue(ValidationIssue.CHUNK_NOT_LOADED, ValidationDecisions.entity(false, false));
        assertIssue(ValidationIssue.ENTITY_MISSING, ValidationDecisions.entity(false, true));
    }

    @Test
    void foreignKeyFailureHasAReadOnlyReportRepresentation() {
        DatabaseIntegrityReport valid = new DatabaseIntegrityReport(List.of());
        DatabaseIntegrityReport invalid = new DatabaseIntegrityReport(List.of("loot_claims row 1"));
        assertTrue(valid.valid());
        assertEquals(false, invalid.valid());
    }

    private void assertIssue(ValidationIssue issue, java.util.Optional<ValidationIssue> actual) {
        assertEquals(issue, actual.orElseThrow());
        assertEquals(
                issue.status(),
                new LootPointValidationResult(null, List.of(issue)).status()
        );
    }
}
