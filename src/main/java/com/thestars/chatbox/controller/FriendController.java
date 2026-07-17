package com.thestars.chatbox.controller;

import com.thestars.chatbox.model.Friendship;
import com.thestars.chatbox.model.User;
import com.thestars.chatbox.service.FriendService;
import com.thestars.chatbox.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Map;
import java.util.Optional;

/**
 * REST API for friend requests and connections between users.
 * Successful actions also push a real-time event to the other party over
 * the "/user/queue/friends" STOMP destination (see WebSocketConfig).
 */
@RestController
@RequestMapping("/api/friends")
public class FriendController {

    private final FriendService friendService;
    private final UserService userService;
    private final SimpMessagingTemplate messagingTemplate;

    public FriendController(FriendService friendService, UserService userService,
                            SimpMessagingTemplate messagingTemplate) {
        this.friendService = friendService;
        this.userService = userService;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * GET /api/friends — List the current user's accepted friends.
     */
    @GetMapping
    public ResponseEntity<?> listFriends(Principal principal) {
        Optional<User> user = currentUser(principal);
        if (user.isEmpty()) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(friendService.getFriends(user.get().getId()));
    }

    /**
     * GET /api/friends/requests/incoming — Pending requests sent to the current user.
     */
    @GetMapping("/requests/incoming")
    public ResponseEntity<?> incomingRequests(Principal principal) {
        Optional<User> user = currentUser(principal);
        if (user.isEmpty()) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(friendService.getIncomingRequests(user.get().getId()));
    }

    /**
     * GET /api/friends/requests/outgoing — Pending requests the current user has sent.
     */
    @GetMapping("/requests/outgoing")
    public ResponseEntity<?> outgoingRequests(Principal principal) {
        Optional<User> user = currentUser(principal);
        if (user.isEmpty()) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(friendService.getOutgoingRequests(user.get().getId()));
    }

    /**
     * GET /api/friends/status/{otherUserId} — Relationship state with a specific user.
     */
    @GetMapping("/status/{otherUserId}")
    public ResponseEntity<?> relationshipStatus(@PathVariable Long otherUserId, Principal principal) {
        Optional<User> user = currentUser(principal);
        if (user.isEmpty()) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(friendService.getRelationship(user.get().getId(), otherUserId));
    }

    /**
     * POST /api/friends/requests — Send a friend request.
     * Body: { "addresseeId": 5 }
     */
    @PostMapping("/requests")
    public ResponseEntity<?> sendRequest(@RequestBody Map<String, Object> body, Principal principal) {
        Optional<User> user = currentUser(principal);
        if (user.isEmpty()) return ResponseEntity.status(401).build();

        Number addresseeId = (Number) body.get("addresseeId");
        if (addresseeId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "addresseeId is required"));
        }

        try {
            Friendship f = friendService.sendRequest(user.get().getId(), addresseeId.longValue());
            f.setRequester(user.get());
            userService.findById(f.getAddresseeId()).ifPresent(f::setAddressee);

            notifyUser(f.getAddressee(), "REQUEST_RECEIVED", f);
            return ResponseEntity.ok(f);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * POST /api/friends/requests/{id}/accept — Accept a pending request.
     */
    @PostMapping("/requests/{id}/accept")
    public ResponseEntity<?> acceptRequest(@PathVariable Long id, Principal principal) {
        Optional<User> user = currentUser(principal);
        if (user.isEmpty()) return ResponseEntity.status(401).build();

        try {
            Friendship f = friendService.acceptRequest(id, user.get().getId());
            userService.findById(f.getRequesterId()).ifPresent(f::setRequester);
            f.setAddressee(user.get());

            notifyUser(f.getRequester(), "REQUEST_ACCEPTED", f);
            return ResponseEntity.ok(f);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * DELETE /api/friends/{id} — Cancel a pending request, decline one, or unfriend an
     * accepted connection. Either side of the relationship may call this.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> removeRelationship(@PathVariable Long id, Principal principal) {
        Optional<User> user = currentUser(principal);
        if (user.isEmpty()) return ResponseEntity.status(401).build();

        try {
            Friendship removed = friendService.removeRelationship(id, user.get().getId());
            Long otherUserId = removed.getRequesterId().equals(user.get().getId())
                    ? removed.getAddresseeId() : removed.getRequesterId();
            String eventType = "ACCEPTED".equals(removed.getStatus()) ? "FRIEND_REMOVED" : "REQUEST_REMOVED";
            userService.findById(otherUserId).ifPresent(other -> notifyUser(other, eventType, removed));

            return ResponseEntity.ok(Map.of("success", true));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }

    private Optional<User> currentUser(Principal principal) {
        return userService.findByEmail(principal.getName());
    }

    private void notifyUser(User target, String type, Friendship friendship) {
        if (target == null) return;
        messagingTemplate.convertAndSendToUser(target.getEmail(), "/queue/friends", Map.of(
                "type", type,
                "friendship", friendship
        ));
    }
}
