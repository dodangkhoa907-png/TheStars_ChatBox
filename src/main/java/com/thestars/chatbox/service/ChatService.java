package com.thestars.chatbox.service;

import com.thestars.chatbox.dao.*;
import com.thestars.chatbox.model.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Business logic for conversation and participant management.
 */
@Service
public class ChatService {

    private final ConversationDAO conversationDAO;
    private final ParticipantDAO participantDAO;
    private final MessageDAO messageDAO;
    private final UserDAO userDAO;

    public ChatService(ConversationDAO conversationDAO, ParticipantDAO participantDAO,
                       MessageDAO messageDAO, UserDAO userDAO) {
        this.conversationDAO = conversationDAO;
        this.participantDAO = participantDAO;
        this.messageDAO = messageDAO;
        this.userDAO = userDAO;
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

        // Add creator as ADMIN
        participantDAO.addParticipant(conversation.getId(), creatorId, "ADMIN");

        // Add members
        for (Long memberId : memberIds) {
            if (!memberId.equals(creatorId)) {
                participantDAO.addParticipant(conversation.getId(), memberId, "MEMBER");
            }
        }

        return conversation;
    }

    /**
     * Add a member to a group conversation.
     */
    public void addMember(Long conversationId, Long userId) {
        if (!participantDAO.isParticipant(conversationId, userId)) {
            participantDAO.addParticipant(conversationId, userId, "MEMBER");
        }
    }

    /**
     * Remove a member from a group conversation.
     */
    public void removeMember(Long conversationId, Long userId) {
        participantDAO.removeParticipant(conversationId, userId);
    }

    /**
     * Get conversation details with participants.
     */
    public Optional<Conversation> getConversationWithParticipants(Long conversationId) {
        Optional<Conversation> conv = conversationDAO.findById(conversationId);
        conv.ifPresent(c -> {
            List<Participant> participants = participantDAO.findByConversationId(conversationId);
            List<Long> userIds = participants.stream().map(Participant::getUserId).toList();
            List<User> users = userDAO.findByIds(userIds);
            c.setParticipants(users);
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
     * Mark all messages as read in a conversation for a user.
     */
    public void markAsRead(Long conversationId, Long userId) {
        participantDAO.updateLastRead(conversationId, userId);
    }
}
