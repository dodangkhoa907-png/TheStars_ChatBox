package com.thestars.chatbox.config;

import com.thestars.chatbox.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

/**
 * Scheduled fallback for presence: flips a user OFFLINE if no heartbeat
 * ping has arrived in over a minute, even though their WebSocket session
 * (per {@link PresenceEventListener}) never sent a clean disconnect frame.
 */
@Component
public class PresenceSweepJob {

    private static final Logger log = LoggerFactory.getLogger(PresenceSweepJob.class);
    private static final long STALE_AFTER_SECONDS = 60;

    private final PresenceTracker presenceTracker;
    private final UserService userService;
    private final SimpMessagingTemplate messagingTemplate;

    public PresenceSweepJob(PresenceTracker presenceTracker, UserService userService,
                            SimpMessagingTemplate messagingTemplate) {
        this.presenceTracker = presenceTracker;
        this.userService = userService;
        this.messagingTemplate = messagingTemplate;
    }

    @Scheduled(fixedRate = 60_000)
    public void sweep() {
        Instant cutoff = Instant.now().minusSeconds(STALE_AFTER_SECONDS);

        for (Map.Entry<String, Instant> entry : presenceTracker.snapshot().entrySet()) {
            if (entry.getValue().isBefore(cutoff)) {
                String email = entry.getKey();
                userService.findByEmail(email).ifPresent(user -> {
                    userService.setOffline(user.getId());
                    messagingTemplate.convertAndSend("/topic/presence",
                            Map.of("userId", user.getId(), "status", "OFFLINE"));
                    log.info("Presence sweep: {} marked OFFLINE (no heartbeat for {}s)",
                            user.getDisplayName(), STALE_AFTER_SECONDS);
                });
                presenceTracker.forget(email);
            }
        }
    }
}
