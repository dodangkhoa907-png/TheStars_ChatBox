package com.thestars.chatbox.service;

import com.thestars.chatbox.dao.FriendshipDAO;
import com.thestars.chatbox.dao.UserDAO;
import com.thestars.chatbox.model.Friendship;
import com.thestars.chatbox.model.User;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Business logic for friend requests and connections between users.
 */
@Service
public class FriendService {

    private final FriendshipDAO friendshipDAO;
    private final UserDAO userDAO;

    public FriendService(FriendshipDAO friendshipDAO, UserDAO userDAO) {
        this.friendshipDAO = friendshipDAO;
        this.userDAO = userDAO;
    }

    /**
     * Send a friend request. Fails if any relationship already exists between the two users.
     */
    public Friendship sendRequest(Long requesterId, Long addresseeId) {
        if (requesterId.equals(addresseeId)) {
            throw new IllegalArgumentException("Cannot send a friend request to yourself");
        }
        if (userDAO.findById(addresseeId).isEmpty()) {
            throw new IllegalArgumentException("User not found");
        }
        if (friendshipDAO.findBetween(requesterId, addresseeId).isPresent()) {
            throw new IllegalStateException("A relationship with this user already exists");
        }
        return friendshipDAO.create(requesterId, addresseeId);
    }

    /**
     * Accept a pending request. Only the addressee may accept.
     */
    public Friendship acceptRequest(Long friendshipId, Long currentUserId) {
        Friendship f = friendshipDAO.findById(friendshipId)
                .orElseThrow(() -> new IllegalArgumentException("Friend request not found"));
        if (!f.getAddresseeId().equals(currentUserId)) {
            throw new IllegalStateException("Only the recipient can accept this request");
        }
        if (!"PENDING".equals(f.getStatus())) {
            throw new IllegalStateException("Request is no longer pending");
        }
        friendshipDAO.updateStatus(f.getId(), "ACCEPTED");
        f.setStatus("ACCEPTED");
        return f;
    }

    /**
     * Remove a relationship row — covers cancelling an outgoing request, declining an
     * incoming one, and unfriending an accepted connection. Either party may call this.
     */
    public Friendship removeRelationship(Long friendshipId, Long currentUserId) {
        Friendship f = friendshipDAO.findById(friendshipId)
                .orElseThrow(() -> new IllegalArgumentException("Relationship not found"));
        if (!f.getRequesterId().equals(currentUserId) && !f.getAddresseeId().equals(currentUserId)) {
            throw new IllegalStateException("Not a participant in this relationship");
        }
        friendshipDAO.deleteById(friendshipId);
        return f;
    }

    public List<Friendship> getIncomingRequests(Long userId) {
        return enrich(friendshipDAO.findIncomingPending(userId));
    }

    public List<Friendship> getOutgoingRequests(Long userId) {
        return enrich(friendshipDAO.findOutgoingPending(userId));
    }

    /**
     * Accepted friendships of a user, enriched with both users so the UI can show
     * who's on the other side of each pair and still act on the friendship (e.g. remove it).
     */
    public List<Friendship> getFriends(Long userId) {
        return enrich(friendshipDAO.findAccepted(userId));
    }

    /**
     * Relationship snapshot between the current user and another user, for UI state
     * (e.g. showing "Add Friend" vs "Pending" vs "Friends" on a search result).
     */
    public Map<String, Object> getRelationship(Long currentUserId, Long otherUserId) {
        Optional<Friendship> existing = friendshipDAO.findBetween(currentUserId, otherUserId);
        if (existing.isEmpty()) {
            return Map.of("status", "NONE");
        }
        Friendship f = existing.get();
        String direction = f.getRequesterId().equals(currentUserId) ? "OUTGOING" : "INCOMING";
        return Map.of(
                "status", f.getStatus(),
                "friendshipId", f.getId(),
                "direction", direction
        );
    }

    private List<Friendship> enrich(List<Friendship> friendships) {
        if (friendships == null || friendships.isEmpty()) return friendships;

        List<Long> userIds = friendships.stream()
                .flatMap(f -> java.util.stream.Stream.of(f.getRequesterId(), f.getAddresseeId()))
                .distinct()
                .toList();

        Map<Long, User> userMap = userDAO.findByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        friendships.forEach(f -> {
            f.setRequester(userMap.get(f.getRequesterId()));
            f.setAddressee(userMap.get(f.getAddresseeId()));
        });
        return friendships;
    }
}
