package com.thestars.chatbox.config;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks the last heartbeat ping received from each connected user.
 *
 * This is a safety net on top of {@link PresenceEventListener}'s WebSocket
 * connect/disconnect tracking: a network drop that never sends a clean
 * disconnect frame (cable pulled, laptop sleeps) leaves that listener with a
 * "session" that's technically still open. {@link PresenceSweepJob} uses this
 * tracker to notice the missing heartbeats and correct the status.
 */
@Component
public class PresenceTracker {

    private final Map<String, Instant> lastPingByEmail = new ConcurrentHashMap<>();

    public void touch(String email) {
        lastPingByEmail.put(email, Instant.now());
    }

    public void forget(String email) {
        lastPingByEmail.remove(email);
    }

    public Map<String, Instant> snapshot() {
        return Map.copyOf(lastPingByEmail);
    }
}
