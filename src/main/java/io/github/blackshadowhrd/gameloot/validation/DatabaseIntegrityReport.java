package io.github.blackshadowhrd.gameloot.validation;

import java.util.List;

public record DatabaseIntegrityReport(List<String> violations) {
    public DatabaseIntegrityReport {
        violations = List.copyOf(violations);
    }

    public boolean valid() { return violations.isEmpty(); }
}
