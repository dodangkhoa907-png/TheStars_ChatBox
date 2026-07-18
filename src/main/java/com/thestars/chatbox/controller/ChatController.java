package com.thestars.chatbox.controller;

import com.thestars.chatbox.model.Conversation;
import com.thestars.chatbox.model.Message;
import com.thestars.chatbox.model.User;
import com.thestars.chatbox.service.ChatService;
import com.thestars.chatbox.service.MessageService;
import com.thestars.chatbox.service.UserService;
import org.springframework.http.ResponseEntity;
import java.security.Principal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * REST API for conversation management.
 */
@RestController
@RequestMapping("/api/conversations")
public class ChatController {

    private final ChatService chatService;
    private final MessageService messageService;
    private final UserService userService;

    public ChatController(ChatService chatService, MessageService messageService,
                          UserService userService) {
        this.chatService = chatService;
        this.messageService = messageService;
        this.userService = userService;
    }

    /**
     * GET /api/conversations — List all conversations for the current user.
     */
    @GetMapping
    public ResponseEntity<?> listConversations(Principal principal) {
        Optional<User> user = userService.findByEmail(principal.getName());
        if (user.isEmpty()) return ResponseEntity.status(401).build();

        List<Conversation> conversations = chatService.getUserConversations(user.get().getId());
        return ResponseEntity.ok(conversations);
    }

    /**
     * GET /api/conversations/available — Get group conversations that the current user has not joined.
     */
    @GetMapping("/available")
    public ResponseEntity<?> getAvailableGroups(Principal principal) {
        Optional<User> user = userService.findByEmail(principal.getName());
        if (user.isEmpty()) return ResponseEntity.status(401).build();

        List<Conversation> available = chatService.getAvailableGroups(user.get().getId());
        return ResponseEntity.ok(available);
    }

    /**
     * GET /api/conversations/{id} — Get conversation details with participants.
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getConversation(@PathVariable Long id,
                                             Principal principal) {
        Optional<User> user = userService.findByEmail(principal.getName());
        if (user.isEmpty()) return ResponseEntity.status(401).build();

        if (!chatService.isParticipant(id, user.get().getId())) {
            return ResponseEntity.status(403).body(Map.of("error", "Not a participant"));
        }

        return chatService.getConversationWithParticipants(id, user.get().getId())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * POST /api/conversations — Create a new conversation.
     * Body: { "type": "SINGLE|GROUP", "name": "Group Name", "memberIds": [1, 2, 3] }
     */
    @PostMapping
    public ResponseEntity<?> createConversation(@RequestBody Map<String, Object> body,
                                                Principal principal) {
        Optional<User> user = userService.findByEmail(principal.getName());
        if (user.isEmpty()) return ResponseEntity.status(401).build();

        String type = (String) body.getOrDefault("type", "SINGLE");
        Long currentUserId = user.get().getId();

        if ("SINGLE".equals(type)) {
            // 1-1 conversation
            Number otherUserId = (Number) body.get("userId");
            if (otherUserId == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "userId is required"));
            }
            Conversation conv = chatService.getOrCreateSingleConversation(
                    currentUserId, otherUserId.longValue());
            return ResponseEntity.ok(conv);
        } else {
            // Group conversation
            String name = (String) body.get("name");
            @SuppressWarnings("unchecked")
            List<Number> memberIdNumbers = (List<Number>) body.get("memberIds");
            if (name == null || memberIdNumbers == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "name and memberIds are required"));
            }
            if (name.length() > 70) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Group name must be under 70 characters"));
            }
            List<Long> memberIds = memberIdNumbers.stream()
                    .map(Number::longValue)
                    .toList();
            Conversation conv = chatService.createGroup(name, currentUserId, memberIds);
            return ResponseEntity.ok(conv);
        }
    }

    /**
     * GET /api/conversations/{id}/messages — Get paginated messages.
     */
    @GetMapping("/{id}/messages")
    public ResponseEntity<?> getMessages(@PathVariable Long id,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "50") int size,
                                         Principal principal) {
        Optional<User> user = userService.findByEmail(principal.getName());
        if (user.isEmpty()) return ResponseEntity.status(401).build();

        if (!chatService.isParticipant(id, user.get().getId())) {
            return ResponseEntity.status(403).body(Map.of("error", "Not a participant"));
        }

        List<Message> messages = messageService.getMessages(id, page, size, user.get().getId());

        // Mark as read
        chatService.markAsRead(id, user.get().getId());

        return ResponseEntity.ok(messages);
    }

    /**
     * POST /api/conversations/{id}/participants — Add a member.
     * Body: { "userId": 5 }
     */
    @PostMapping("/{id}/participants")
    public ResponseEntity<?> addParticipant(@PathVariable Long id,
                                            @RequestBody Map<String, Object> body,
                                            Principal principal) {
        Optional<User> user = userService.findByEmail(principal.getName());
        if (user.isEmpty()) return ResponseEntity.status(401).build();

        Number userId = (Number) body.get("userId");
        if (userId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "userId is required"));
        }

        chatService.addMember(id, userId.longValue(), user.get().getId());
        return ResponseEntity.ok(Map.of("success", true));
    }

    /**
     * DELETE /api/conversations/{id}/participants/{userId} — Kick a member, or leave
     * (self-removal is routed through {@link #leaveGroup} so an owner can never
     * accidentally orphan the group — ownership auto-transfers first).
     * Kicking someone else requires the caller to be the OWNER, or a DEPUTY kicking
     * a plain MEMBER (a deputy can't kick the owner or another deputy).
     */
    @DeleteMapping("/{id}/participants/{userId}")
    public ResponseEntity<?> removeParticipant(@PathVariable Long id,
                                               @PathVariable Long userId,
                                               Principal principal) {
        Optional<User> user = userService.findByEmail(principal.getName());
        if (user.isEmpty()) return ResponseEntity.status(401).build();
        Long callerId = user.get().getId();

        if (userId.equals(callerId)) {
            try {
                chatService.leaveGroup(id, callerId, null);
                return ResponseEntity.ok(Map.of("success", true));
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
            }
        }

        if (chatService.isOwner(id, callerId)) {
            // owner can kick anyone
        } else if (chatService.isAdmin(id, callerId) && !chatService.isAdmin(id, userId)) {
            // deputy can only kick a plain member
        } else {
            return ResponseEntity.status(403).body(Map.of("error", "You don't have permission to remove this member"));
        }

        chatService.removeMember(id, userId, callerId);
        return ResponseEntity.ok(Map.of("success", true));
    }

    /**
     * POST /api/conversations/{id}/leave — Leave a group.
     * Body (optional): { "newOwnerId": 5 } — required to be provided by the client
     * when the caller is the owner and hasn't specified who should take over; if
     * omitted the server auto-picks (earliest-joined deputy, else earliest member).
     */
    @PostMapping("/{id}/leave")
    public ResponseEntity<?> leaveGroup(@PathVariable Long id,
                                        @RequestBody(required = false) Map<String, Object> body,
                                        Principal principal) {
        Optional<User> user = userService.findByEmail(principal.getName());
        if (user.isEmpty()) return ResponseEntity.status(401).build();

        Number newOwnerId = body != null ? (Number) body.get("newOwnerId") : null;
        try {
            chatService.leaveGroup(id, user.get().getId(), newOwnerId != null ? newOwnerId.longValue() : null);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * POST /api/conversations/{id}/participants/{userId}/promote — Promote a member to
     * DEPUTY ("pho nhom"). Only the group OWNER may appoint deputies.
     */
    @PostMapping("/{id}/participants/{userId}/promote")
    public ResponseEntity<?> promote(@PathVariable Long id, @PathVariable Long userId, Principal principal) {
        Optional<User> user = userService.findByEmail(principal.getName());
        if (user.isEmpty()) return ResponseEntity.status(401).build();

        if (!chatService.isOwner(id, user.get().getId())) {
            return ResponseEntity.status(403).body(Map.of("error", "Only the group owner can appoint deputies"));
        }

        chatService.promoteToDeputy(id, userId);
        return ResponseEntity.ok(Map.of("success", true));
    }

    /**
     * POST /api/conversations/{id}/participants/{userId}/demote — Demote a deputy
     * back to a plain member. Only the group OWNER may do this.
     */
    @PostMapping("/{id}/participants/{userId}/demote")
    public ResponseEntity<?> demote(@PathVariable Long id, @PathVariable Long userId, Principal principal) {
        Optional<User> user = userService.findByEmail(principal.getName());
        if (user.isEmpty()) return ResponseEntity.status(401).build();

        if (!chatService.isOwner(id, user.get().getId())) {
            return ResponseEntity.status(403).body(Map.of("error", "Only the group owner can demote a deputy"));
        }

        chatService.demoteToMember(id, userId);
        return ResponseEntity.ok(Map.of("success", true));
    }

    /**
     * POST /api/conversations/{id}/transfer-ownership — Hand group ownership to
     * another member without leaving. Body: { "newOwnerId": 5 }
     */
    @PostMapping("/{id}/transfer-ownership")
    public ResponseEntity<?> transferOwnership(@PathVariable Long id, @RequestBody Map<String, Object> body,
                                               Principal principal) {
        Optional<User> user = userService.findByEmail(principal.getName());
        if (user.isEmpty()) return ResponseEntity.status(401).build();

        if (!chatService.isOwner(id, user.get().getId())) {
            return ResponseEntity.status(403).body(Map.of("error", "Only the group owner can transfer ownership"));
        }

        Number newOwnerId = (Number) body.get("newOwnerId");
        if (newOwnerId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "newOwnerId is required"));
        }

        try {
            chatService.transferOwnership(id, user.get().getId(), newOwnerId.longValue());
            return ResponseEntity.ok(Map.of("success", true));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * POST /api/conversations/{id}/read — Mark conversation as read.
     */
    @PostMapping("/{id}/read")
    public ResponseEntity<?> markAsRead(@PathVariable Long id,
                                        Principal principal) {
        Optional<User> user = userService.findByEmail(principal.getName());
        if (user.isEmpty()) return ResponseEntity.status(401).build();

        chatService.markAsRead(id, user.get().getId());
        return ResponseEntity.ok(Map.of("success", true));
    }

    /**
     * GET /api/users/search — Search users by name.
     */
    @GetMapping("/users/search")
    public ResponseEntity<?> searchUsers(@RequestParam String q) {
        return ResponseEntity.ok(userService.searchByName(q));
    }

    /**
     * GET /api/users — List all users.
     */
    @GetMapping("/users")
    public ResponseEntity<?> listUsers() {
        return ResponseEntity.ok(userService.findAll());
    }

    /**
     * GET /api/users/{id} — A user's profile (clicking a chat header to view who
     * you're talking to). password is never serialized (see User.password).
     */
    @GetMapping("/users/{id}")
    public ResponseEntity<?> getUserProfile(@PathVariable Long id, Principal principal) {
        if (userService.findByEmail(principal.getName()).isEmpty()) return ResponseEntity.status(401).build();

        return userService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
