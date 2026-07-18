package com.thestars.chatbox.controller;

import com.thestars.chatbox.dao.NotificationDAO;
import com.thestars.chatbox.model.Notification;
import com.thestars.chatbox.model.User;
import com.thestars.chatbox.service.MessageService;
import com.thestars.chatbox.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * REST API for @mention notifications.
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationDAO notificationDAO;
    private final MessageService messageService;
    private final UserService userService;

    public NotificationController(NotificationDAO notificationDAO, MessageService messageService,
                                  UserService userService) {
        this.notificationDAO = notificationDAO;
        this.messageService = messageService;
        this.userService = userService;
    }

    /**
     * GET /api/notifications — Most recent notifications, enriched with which
     * conversation to jump to.
     */
    @GetMapping
    public ResponseEntity<?> listRecent(Principal principal) {
        Optional<User> user = userService.findByEmail(principal.getName());
        if (user.isEmpty()) return ResponseEntity.status(401).build();

        List<Notification> notifications = notificationDAO.findRecentByUser(user.get().getId(), 20);
        notifications.forEach(n -> messageService.findById(n.getMessageId())
                .ifPresent(m -> n.setConversationId(m.getConversationId())));

        return ResponseEntity.ok(notifications);
    }

    /**
     * GET /api/notifications/unread-count
     */
    @GetMapping("/unread-count")
    public ResponseEntity<?> unreadCount(Principal principal) {
        Optional<User> user = userService.findByEmail(principal.getName());
        if (user.isEmpty()) return ResponseEntity.status(401).build();

        return ResponseEntity.ok(Map.of("count", notificationDAO.countUnread(user.get().getId())));
    }

    /**
     * POST /api/notifications/{id}/read
     */
    @PostMapping("/{id}/read")
    public ResponseEntity<?> markRead(@PathVariable Long id, Principal principal) {
        Optional<User> user = userService.findByEmail(principal.getName());
        if (user.isEmpty()) return ResponseEntity.status(401).build();

        notificationDAO.markRead(id, user.get().getId());
        return ResponseEntity.ok(Map.of("success", true));
    }

    /**
     * POST /api/notifications/read-all
     */
    @PostMapping("/read-all")
    public ResponseEntity<?> markAllRead(Principal principal) {
        Optional<User> user = userService.findByEmail(principal.getName());
        if (user.isEmpty()) return ResponseEntity.status(401).build();

        notificationDAO.markAllRead(user.get().getId());
        return ResponseEntity.ok(Map.of("success", true));
    }
}
