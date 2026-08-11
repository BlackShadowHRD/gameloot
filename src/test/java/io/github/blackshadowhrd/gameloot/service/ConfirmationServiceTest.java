package io.github.blackshadowhrd.gameloot.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfirmationServiceTest {
    @Test
    void confirmationIsSenderScopedSingleUseAndExpires() {
        MutableClock clock = new MutableClock();
        ConfirmationService confirmations = new ConfirmationService(clock, Duration.ofSeconds(30));

        confirmations.request("player:a", "reset-all");
        assertFalse(confirmations.consume("player:b", "reset-all"));
        assertTrue(confirmations.consume("player:a", "reset-all"));
        assertFalse(confirmations.consume("player:a", "reset-all"));

        confirmations.request("console", "reset-all");
        clock.advance(Duration.ofSeconds(31));
        assertFalse(confirmations.consume("console", "reset-all"));
    }

    private static final class MutableClock extends Clock {
        private Instant instant = Instant.EPOCH;
        void advance(Duration duration) { instant = instant.plus(duration); }
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }
}
