package com.thestars.chatbox.service;

import com.thestars.chatbox.dao.*;
import com.thestars.chatbox.model.*;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Business logic for conversation and participant management.
 */
@Service
public class ChatService {

    private final ConversationDAO conversationDAO;
    private final ParticipantDAO participantDAO;
    private final MessageDAO messageDAO;
    private final UserDAO userDAO;
    private final MessageService messageService;
    private final MessageReadDAO messageReadDAO;
    private final SimpMessagingTemplate messagingTemplate;

    public ChatService(ConversationDAO conversationDAO, ParticipantDAO participantDAO,
                       MessageDAO messageDAO, UserDAO userDAO,
                       MessageService messageService, MessageReadDAO messageReadDAO,
                       SimpMessagingTemplate messagingTemplate) {
        this.conversationDAO = conversationDAO;
        this.participantDAO = participantDAO;
        this.messageDAO = messageDAO;
        this.userDAO = userDAO;
        this.messageService = messageService;
        this.messageReadDAO = messageReadDAO;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Get all conversations for a user with last message preview and unread count.
     */
    public List<Conversation> getUserConversations(Long userId) {
        return conversationDAO.findByUserIdWithDetails(userId);
    }

    /**
     * Create a new 1-1 conversation between two users.
     * Returns existing conversation if one already exists.
     */
    @Transactional
    public Conversation getOrCreateSingleConversation(Long userId1, Long userId2) {
        // Check for existing conversation
        Optional<Conversation> existing = conversationDAO.findSingleConversation(userId1, userId2);
        if (existing.isPresent()) {
            return existing.get();
        }

        // Create new conversation
        Conversation conversation = Conversation.builder()
                .type("SINGLE")
                .createdBy(userId1)
                .build();
        conversationDAO.create(conversation);

        // Add both participants
        participantDAO.addParticipant(conversation.getId(), userId1, "MEMBER");
        participantDAO.addParticipant(conversation.getId(), userId2, "MEMBER");

        return conversation;
    }

    /**
     * Create a new group conversation.
     */
    @Transactional
    public Conversation createGroup(String name, Long creatorId, List<Long> memberIds) {
        Conversation conversation = Conversation.builder()
                .name(name)
                .type("GROUP")
                .createdBy(creatorId)
                .build();
        conversationDAO.create(conversation);

        // Creator becomes the group's OWNER ("nhom truong")
        participantDAO.addParticipant(conversation.getId(), creatorId, "OWNER");

        // Add members
        for (Long memberId : memberIds) {
            if (!memberId.equals(creatorId)) {
                participantDAO.addParticipant(conversation.getId(), memberId, "MEMBER");
            }
        }

        return conversation;
    }

    /**
     * Add a member to a group conversation, posting a SYSTEM message announcing it.
     */
    public void addMember(Long conversationId, Long userId, Long actingUserId) {
        if (!participantDAO.isParticipant(conversationId, userId)) {
            participantDAO.addParticipant(conversationId, userId, "MEMBER");
            postSystemMessage(conversationId, actingUserId, userId, "added");
        }
    }

    /**
     * Kick a member from a group conversation (caller is someone else), posting a
     * SYSTEM message announcing it. Permission (owner can kick anyone, a deputy can
     * only kick plain members) is enforced by the caller in the controller layer.
     */
    public void removeMember(Long conversationId, Long userId, Long actingUserId) {
        participantDAO.removeParticipant(conversationId, userId);
        postSystemMessage(conversationId, actingUserId, userId, "removed");
    }

    /**
     * Promote an existing member to DEPUTY ("pho nhom") within a group. Only the
     * group's OWNER may do this — enforced by the caller.
     */
    public void promoteToDeputy(Long conversationId, Long userId) {
        participantDAO.updateRole(conversationId, userId, "DEPUTY");
    }

    /**
     * Demote a deputy back to a plain MEMBER. Only the group's OWNER may do this.
     */
    public void demoteToMember(Long conversationId, Long userId) {
        participantDAO.updateRole(conversationId, userId, "MEMBER");
    }

    /**
     * Hand group ownership to another existing participant. The outgoing owner
     * becomes a DEPUTY rather than a plain member, keeping their management
     * privileges after stepping down.
     */
    @Transactional
    public void transferOwnership(Long conversationId, Long currentOwnerId, Long newOwnerId) {
        if (currentOwnerId.equals(newOwnerId)) {
            throw new IllegalArgumentException("Already the group owner");
        }
        if (!participantDAO.isParticipant(conversationId, newOwnerId)) {
            throw new IllegalArgumentException("That user is not a member of this group");
        }
        participantDAO.updateRole(conversationId, newOwnerId, "OWNER");
        participantDAO.updateRole(conversationId, currentOwnerId, "DEPUTY");

        String newOwnerName = userDAO.findById(newOwnerId).map(User::getDisplayName).orElse("a member");
        String actorName = userDAO.findById(currentOwnerId).map(User::getDisplayName).orElse("Someone");
        Message message = messageService.sendMessage(conversationId, currentOwnerId,
                actorName + " made " + newOwnerName + " the group owner", "SYSTEM");
        messagingTemplate.convertAndSend("/topic/conversation/" + conversationId, message);
    }

    /**
     * Leave a group. Handles the three Zalo-style cases:
     *  - Plain member or deputy: just leave.
     *  - Owner, other members remain: ownership must pass on first — to
     *    {@code preferredNewOwnerId} if given (validated as an existing participant),
     *    otherwise auto-picked (earliest-joined deputy, else earliest-joined member)
     *    so a group can never end up without an owner.
     *  - Owner, sole remaining member: the group is dissolved (deleted outright).
     */
    @Transactional
    public void leaveGroup(Long conversationId, Long userId, Long preferredNewOwnerId) {
        if (!participantDAO.isParticipant(conversationId, userId)) {
            throw new IllegalArgumentException("Not a member of this group");
        }

        String role = participantDAO.findRole(conversationId, userId).orElse("MEMBER");
        List<Participant> others = participantDAO.findByConversationId(conversationId).stream()
                .filter(p -> !p.getUserId().equals(userId))
                .toList();

        if (!"OWNER".equals(role)) {
            participantDAO.removeParticipant(conversationId, userId);
            postSystemMessage(conversationId, userId, userId, "removed");
            return;
        }

        if (others.isEmpty()) {
            messageDAO.clearThreadParents(conversationId);
            conversationDAO.delete(conversationId);
            return;
        }

        Long newOwnerId;
        if (preferredNewOwnerId != null) {
            if (others.stream().noneMatch(p -> p.getUserId().equals(preferredNewOwnerId))) {
                throw new IllegalArgumentException("That user is not a member of this group");
            }
            newOwnerId = preferredNewOwnerId;
        } else {
            newOwnerId = others.stream()
                    .filter(p -> "DEPUTY".equals(p.getRole()))
                    .findFirst()
                    .or(() -> others.stream().findFirst())
                    .map(Participant::getUserId)
                    .orElseThrow();
        }

        participantDAO.updateRole(conversationId, newOwnerId, "OWNER");
        participantDAO.removeParticipant(conversationId, userId);
        postSystemMessage(conversationId, userId, userId, "removed");

        String newOwnerName = userDAO.findById(newOwnerId).map(User::getDisplayName).orElse("a member");
        Message message = messageService.sendMessage(conversationId, newOwnerId,
                newOwnerName + " is now the group owner", "SYSTEM");
        messagingTemplate.convertAndSend("/topic/conversation/" + conversationId, message);
    }

    /**
     * Whether the given user can manage this group's membership (owner or deputy).
     * False if they're not even a member.
     */
    public boolean isAdmin(Long conversationId, Long userId) {
        return participantDAO.findRole(conversationId, userId)
                .map(role -> "OWNER".equals(role) || "DEPUTY".equals(role))
                .orElse(false);
    }

    /**
     * Whether the given user is the group's OWNER ("nhom truong").
     */
    public boolean isOwner(Long conversationId, Long userId) {
        return participantDAO.findRole(conversationId, userId)
                .map("OWNER"::equals)
                .orElse(false);
    }

    /**
     * Insert a SYSTEM message describing a membership change and broadcast it live,
     * the same way a normal chat message is delivered.
     */
    private void postSystemMessage(Long conversationId, Long actorId, Long targetId, String action) {
        String actorName = userDAO.findById(actorId).map(User::getDisplayName).orElse("Someone");
        String content;

        if (actorId.equals(targetId)) {
            // Self-service actions read better in first person ("Alice joined the group"
            // instead of "Alice added Alice") — covers joining via Browse Groups and leaving.
            content = actorName + (action.equals("added") ? " joined the group" : " left the group");
        } else {
            String targetName = userDAO.findById(targetId).map(User::getDisplayName).orElse("a member");
            content = actorName + " " + action + " " + targetName;
        }

        Message message = messageService.sendMessage(conversationId, actorId, content, "SYSTEM");
        messagingTemplate.convertAndSend("/topic/conversation/" + conversationId, message);
    }

    /**
     * Get conversation details with participants (each enriched with their user profile),
     * plus whether the requesting user is an admin of this conversation.
     */
    public Optional<Conversation> getConversationWithParticipants(Long conversationId, Long requestingUserId) {
        Optional<Conversation> conv = conversationDAO.findById(conversationId);
        conv.ifPresent(c -> {
            List<Participant> participants = participantDAO.findByConversationId(conversationId);
            List<Long> userIds = participants.stream().map(Participant::getUserId).toList();
            Map<Long, User> usersById = userDAO.findByIds(userIds).stream()
                    .collect(Collectors.toMap(User::getId, u -> u));
            participants.forEach(p -> p.setUser(usersById.get(p.getUserId())));
            c.setParticipants(participants);
            String myRole = participants.stream()
                    .filter(p -> p.getUserId().equals(requestingUserId))
                    .map(Participant::getRole)
                    .findFirst()
                    .orElse(null);
            c.setCurrentUserRole(myRole);
            c.setCurrentUserIsAdmin("OWNER".equals(myRole) || "DEPUTY".equals(myRole));
        });
        return conv;
    }

    /**
     * Get all GROUP conversations that a user does NOT participate in.
     */
    public List<Conversation> getAvailableGroups(Long userId) {
        return conversationDAO.findAvailableGroups(userId);
    }

    /**
     * Check if a user is a participant of a conversation.
     */
    public boolean isParticipant(Long conversationId, Long userId) {
        return participantDAO.isParticipant(conversationId, userId);
    }

    /**
     * Mark all messages as read in a conversation for a user — both the cheap
     * unread-count marker and the granular per-message read receipts, then
     * broadcast so the sender's open chat updates their ticks live.
     */
    public void markAsRead(Long conversationId, Long userId) {
        participantDAO.updateLastRead(conversationId, userId);
        messageReadDAO.markConversationRead(conversationId, userId);
        messagingTemplate.convertAndSend("/topic/conversation/" + conversationId + "/read-state",
                Map.of("type", "CONVERSATION_READ", "readerId", userId));
    }
}
