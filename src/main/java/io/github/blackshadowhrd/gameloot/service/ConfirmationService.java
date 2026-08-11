package io.github.blackshadowhrd.gameloot.service;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ConfirmationService {
    private final Clock clock;
    private final Duration validity;
    private final Map<String, PendingConfirmation> confirmations = new ConcurrentHashMap<>();

    public ConfirmationService(Duration validity) {
        this(Clock.systemUTC(), validity);
    }

    ConfirmationService(Clock clock, Duration validity) {
        this.clock = clock;
        this.validity = validity;
    }

    public void request(String senderKey, String action) {
        purgeExpired();
        confirmations.put(senderKey, new PendingConfirmation(action, clock.millis() + validity.toMillis()));
    }

    public boolean consume(String senderKey, String action) {
        purgeExpired();
        PendingConfirmation confirmation = confirmations.remove(senderKey);
        return confirmation != null && confirmation.expiresAt() >= clock.millis()
                && confirmation.action().equals(action);
    }

    public void purgeExpired() {
        long now = clock.millis();
        confirmations.entrySet().removeIf(entry -> entry.getValue().expiresAt() < now);
    }

    private record PendingConfirmation(String action, long expiresAt) { }
}
