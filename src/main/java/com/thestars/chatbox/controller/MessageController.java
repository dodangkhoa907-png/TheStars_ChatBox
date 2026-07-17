package com.thestars.chatbox.controller;

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

    public MessageController(MessageService messageService, ChatService chatService,
                             UserService userService, SimpMessagingTemplate messagingTemplate) {
        this.messageService = messageService;
        this.chatService = chatService;
        this.userService = userService;
        this.messagingTemplate = messagingTemplate;
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
}
