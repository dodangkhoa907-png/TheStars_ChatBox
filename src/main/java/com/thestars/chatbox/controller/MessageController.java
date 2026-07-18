package com.thestars.chatbox.controller;

import com.thestars.chatbox.config.PresenceTracker;
import com.thestars.chatbox.model.Message;
import com.thestars.chatbox.model.User;
import com.thestars.chatbox.service.ChatService;
import com.thestars.chatbox.service.MessageService;
import com.thestars.chatbox.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Map;

/**
 * WebSocket message handler using STOMP protocol.
 * Handles real-time message sending and typing indicators.
 */
@Controller
public class MessageController {

    private static final Logger log = LoggerFactory.getLogger(MessageController.class);

    private final MessageService messageService;
    private final ChatService chatService;
    private final UserService userService;
    private final SimpMessagingTemplate messagingTemplate;
    private final PresenceTracker presenceTracker;

    public MessageController(MessageService messageService, ChatService chatService,
                             UserService userService, SimpMessagingTemplate messagingTemplate,
                             PresenceTracker presenceTracker) {
        this.messageService = messageService;
        this.chatService = chatService;
        this.userService = userService;
        this.messagingTemplate = messagingTemplate;
        this.presenceTracker = presenceTracker;
    }

    /**
     * Handle incoming chat messages via WebSocket.
     *
     * Client sends to: /app/chat.send/{conversationId}
     * Server broadcasts to: /topic/conversation/{conversationId}
     */
    @MessageMapping("/chat.send/{conversationId}")
    public void sendMessage(@DestinationVariable Long conversationId,
                            @Payload Map<String, String> payload,
                            Principal principal) {
        if (principal == null) {
            log.warn("Unauthenticated WebSocket message attempt");
            return;
        }

        String email = principal.getName();
        User sender = userService.findByEmail(email).orElse(null);
        if (sender == null) {
            log.warn("User not found for email: {}", email);
            return;
        }

        // Verify participant
        if (!chatService.isParticipant(conversationId, sender.getId())) {
            log.warn("User {} is not a participant of conversation {}", sender.getId(), conversationId);
            return;
        }

        String content = payload.get("content");
        String messageType = payload.getOrDefault("messageType", "TEXT");
        String clientMsgId = payload.get("clientMsgId");

        // Save message to database
        Message message = messageService.sendMessage(conversationId, sender.getId(), content, messageType);
        message.setClientMsgId(clientMsgId);

        log.info("Message sent: conv={}, sender={}, type={}", conversationId, sender.getDisplayName(), messageType);

        // Broadcast to all subscribers of this conversation
        messagingTemplate.convertAndSend(
                "/topic/conversation/" + conversationId,
                message
        );
    }

    /**
     * Handle typing indicator.
     *
     * Client sends to: /app/chat.typing/{conversationId}
     * Server broadcasts to: /topic/conversation/{conversationId}/typing
     */
    @MessageMapping("/chat.typing/{conversationId}")
    public void typingIndicator(@DestinationVariable Long conversationId,
                                Principal principal) {
        if (principal == null) return;

        String email = principal.getName();
        User sender = userService.findByEmail(email).orElse(null);
        if (sender == null) return;

        Map<String, Object> typingEvent = Map.of(
                "userId", sender.getId(),
                "displayName", sender.getDisplayName(),
                "conversationId", conversationId
        );

        messagingTemplate.convertAndSend(
                "/topic/conversation/" + conversationId + "/typing",
                typingEvent
        );
    }

    /**
     * Handle reaction toggle via WebSocket.
     *
     * Client sends to: /app/chat.react/{conversationId}
     * Server broadcasts updated reactions to: /topic/conversation/{conversationId}/reaction
     */
    @MessageMapping("/chat.react/{conversationId}")
    public void toggleReaction(@DestinationVariable Long conversationId,
                               @Payload Map<String, Object> payload,
                               Principal principal) {
        if (principal == null) return;

        String email = principal.getName();
        User sender = userService.findByEmail(email).orElse(null);
        if (sender == null) return;

        Long messageId = ((Number) payload.get("messageId")).longValue();
        String emoji = (String) payload.get("emoji");

        messageService.toggleReaction(messageId, sender.getId(), emoji);

        Map<String, Object> reactionEvent = Map.of(
                "messageId", messageId,
                "userId", sender.getId(),
                "emoji", emoji,
                "displayName", sender.getDisplayName()
        );

        messagingTemplate.convertAndSend(
                "/topic/conversation/" + conversationId + "/reaction",
                reactionEvent
        );
    }

    /**
     * Reply to a message inside its thread.
     *
     * Client sends to: /app/chat.replyThread/{parentId}
     * Server broadcasts the reply to: /topic/thread/{parentId}
     * Server broadcasts the updated reply count to: /topic/conversation/{conversationId}/thread-update
     */
    @MessageMapping("/chat.replyThread/{parentId}")
    public void replyInThread(@DestinationVariable Long parentId,
                              @Payload Map<String, String> payload,
                              Principal principal) {
        if (principal == null) return;
        User sender = userService.findByEmail(principal.getName()).orElse(null);
        if (sender == null) return;

        Message parent = messageService.findById(parentId).orElse(null);
        if (parent == null) return;
        if (!chatService.isParticipant(parent.getConversationId(), sender.getId())) {
            log.warn("User {} is not a participant of conversation {}", sender.getId(), parent.getConversationId());
            return;
        }

        String content = payload.get("content");
        String clientMsgId = payload.get("clientMsgId");

        Message reply = messageService.replyInThread(parentId, sender.getId(), content);
        reply.setClientMsgId(clientMsgId);

        messagingTemplate.convertAndSend("/topic/thread/" + parentId, reply);
        messagingTemplate.convertAndSend("/topic/conversation/" + parent.getConversationId() + "/thread-update",
                Map.of("parentId", parentId, "replyCount", parent.getReplyCount() + 1));
    }

    /**
     * A recipient's client confirms it received a message live over the socket
     * (as opposed to loading it later via history) — the "Delivered" tick state.
     *
     * Client sends to: /app/chat.delivered
     */
    @MessageMapping("/chat.delivered")
    public void markDelivered(@Payload Map<String, Object> payload, Principal principal) {
        if (principal == null) return;
        User user = userService.findByEmail(principal.getName()).orElse(null);
        if (user == null) return;

        Long messageId = ((Number) payload.get("messageId")).longValue();
        Long conversationId = messageService.markDelivered(messageId, user.getId());
        if (conversationId == null) return;

        messagingTemplate.convertAndSend("/topic/conversation/" + conversationId + "/read-state",
                Map.of("type", "MESSAGE_DELIVERED", "messageId", messageId));
    }

    /**
     * Heartbeat — client pings every 30s while connected. {@link com.thestars.chatbox.config.PresenceSweepJob}
     * flips a user OFFLINE if this stops arriving, catching disconnects that never send a clean close frame.
     *
     * Client sends to: /app/presence.ping
     */
    @MessageMapping("/presence.ping")
    public void ping(Principal principal) {
        if (principal != null) {
            presenceTracker.touch(principal.getName());
        }
    }

    /**
     * Client-detected inactivity (no mouse/keyboard for 5 minutes) — marks the user AWAY
     * without waiting for their WebSocket connection to actually drop.
     *
     * Client sends to: /app/presence.idle
     */
    @MessageMapping("/presence.idle")
    public void idle(Principal principal) {
        if (principal == null) return;

        userService.findByEmail(principal.getName()).ifPresent(user -> {
            userService.setStatus(user.getId(), "AWAY");
            messagingTemplate.convertAndSend("/topic/presence",
                    Map.of("userId", user.getId(), "status", "AWAY"));
        });
    }

    /**
     * Client-detected return from idle (mouse/keyboard activity resumed) — flips AWAY
     * back to ONLINE. Deliberately separate from {@link #ping} so the 30s heartbeat
     * doesn't itself cancel idle detection.
     *
     * Client sends to: /app/presence.active
     */
    @MessageMapping("/presence.active")
    public void active(Principal principal) {
        if (principal == null) return;

        userService.findByEmail(principal.getName()).ifPresent(user -> {
            userService.setOnline(user.getId());
            messagingTemplate.convertAndSend("/topic/presence",
                    Map.of("userId", user.getId(), "status", "ONLINE"));
        });
    }
}
