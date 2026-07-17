package com.thestars.chatbox.config;

import com.thestars.chatbox.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.AbstractSubProtocolEvent;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks real user presence from the WebSocket connection lifecycle.
 *
 * Login/logout alone leave stale ONLINE status behind whenever a browser tab
 * is closed without an explicit logout call. This listener flips a user back
 * to OFFLINE only once their last open WebSocket session actually drops, and
 * broadcasts the change so open chats update without a page reload.
 *
 * Sessions are tracked by their actual STOMP session id (not a plain counter):
 * after a server restart or network hiccup, a browser's old session can send
 * its disconnect event *after* the reconnected new session's connect event
 * has already arrived. A counter can't tell those apart and would wrongly
 * flip the user offline; removing the specific old session id from the set
 * leaves the new session id in place, so presence stays correct.
 */
@Component
public class PresenceEventListener {

    private static final Logger log = LoggerFactory.getLogger(PresenceEventListener.class);

    private final UserService userService;
    private final SimpMessagingTemplate messagingTemplate;

    // email -> set of open STOMP session ids (a user may have several tabs/devices open at once).
    private final Map<String, Set<String>> openSessionsByEmail = new ConcurrentHashMap<>();

    public PresenceEventListener(UserService userService, SimpMessagingTemplate messagingTemplate) {
        this.userService = userService;
        this.messagingTemplate = messagingTemplate;
    }

    @EventListener
    public void handleSessionConnected(SessionConnectedEvent event) {
        String email = emailOf(event.getUser());
        String sessionId = sessionIdOf(event);
        if (email == null || sessionId == null) return;

        Set<String> sessions = openSessionsByEmail.computeIfAbsent(email, k -> ConcurrentHashMap.newKeySet());
        boolean wasEmpty = sessions.isEmpty();
        sessions.add(sessionId);
        if (wasEmpty) {
            updatePresence(email, "ONLINE");
        }
    }

    @EventListener
    public void handleSessionDisconnect(SessionDisconnectEvent event) {
        String email = emailOf(event.getUser());
        String sessionId = sessionIdOf(event);
        if (email == null || sessionId == null) return;

        Set<String> sessions = openSessionsByEmail.get(email);
        if (sessions == null) return;

        sessions.remove(sessionId);
        if (sessions.isEmpty()) {
            openSessionsByEmail.remove(email);
            updatePresence(email, "OFFLINE");
        }
    }

    private void updatePresence(String email, String status) {
        userService.findByEmail(email).ifPresent(user -> {
            if ("ONLINE".equals(status)) {
                userService.setOnline(user.getId());
            } else {
                userService.setOffline(user.getId());
            }
            messagingTemplate.convertAndSend("/topic/presence", Map.of(
                    "userId", user.getId(),
                    "status", status
            ));
            log.info("Presence: {} is now {}", user.getDisplayName(), status);
        });
    }

    private String emailOf(Principal principal) {
        return principal != null ? principal.getName() : null;
    }

    private String sessionIdOf(AbstractSubProtocolEvent event) {
        return SimpMessageHeaderAccessor.wrap(event.getMessage()).getSessionId();
    }
}
