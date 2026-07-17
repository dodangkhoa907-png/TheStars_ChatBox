package com.thestars.chatbox.service;

import com.thestars.chatbox.dao.*;
import com.thestars.chatbox.model.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Business logic for message operations.
 */
@Service
public class MessageService {

    private final MessageDAO messageDAO;
    private final AttachmentDAO attachmentDAO;
    private final MessageReactionDAO reactionDAO;
    private final ConversationDAO conversationDAO;
    private final UserDAO userDAO;

    public MessageService(MessageDAO messageDAO, AttachmentDAO attachmentDAO,
                          MessageReactionDAO reactionDAO, ConversationDAO conversationDAO,
                          UserDAO userDAO) {
        this.messageDAO = messageDAO;
        this.attachmentDAO = attachmentDAO;
        this.reactionDAO = reactionDAO;
        this.conversationDAO = conversationDAO;
        this.userDAO = userDAO;
    }

    /**
     * Get paginated messages for a conversation with sender info, attachments, and reactions.
     */
    public List<Message> getMessages(Long conversationId, int page, int size) {
        int offset = page * size;
        List<Message> messages = messageDAO.findByConversationId(conversationId, offset, size);

        if (messages.isEmpty()) return messages;

        // Batch load sender info
        List<Long> senderIds = messages.stream()
                .map(Message::getSenderId)
                .distinct()
                .toList();
        Map<Long, User> senderMap = userDAO.findByIds(senderIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        // Batch load reactions
        List<Long> messageIds = messages.stream().map(Message::getId).toList();
        List<MessageReaction> allReactions = reactionDAO.findByMessageIds(messageIds);
        Map<Long, List<MessageReaction>> reactionMap = allReactions.stream()
                .collect(Collectors.groupingBy(MessageReaction::getMessageId));

        // Enrich messages
        for (Message msg : messages) {
            msg.setSender(senderMap.get(msg.getSenderId()));
            msg.setReactions(reactionMap.getOrDefault(msg.getId(), List.of()));

            // Load attachments for FILE/IMAGE messages
            if ("FILE".equals(msg.getMessageType()) || "IMAGE".equals(msg.getMessageType())) {
                msg.setAttachments(attachmentDAO.findByMessageId(msg.getId()));
            }
        }

        return messages;
    }

    /**
     * Send a new message.
     */
    @Transactional
    public Message sendMessage(Long conversationId, Long senderId, String content, String messageType) {
        Message message = Message.builder()
                .conversationId(conversationId)
                .senderId(senderId)
                .content(content)
                .messageType(messageType != null ? messageType : "TEXT")
                .build();

        message = messageDAO.save(message);

        // Touch conversation updated_at to reorder in sidebar
        conversationDAO.touch(conversationId);

        // Attach sender info for WebSocket broadcast
        userDAO.findById(senderId).ifPresent(message::setSender);

        return message;
    }

    /**
     * Toggle a reaction on a message.
     */
    public void toggleReaction(Long messageId, Long userId, String emoji) {
        reactionDAO.addReaction(messageId, userId, emoji);
    }

    /**
     * Delete a message (soft delete).
     */
    public void deleteMessage(Long messageId) {
        messageDAO.softDelete(messageId);
    }

    /**
     * Get a single message by ID.
     */
    public Optional<Message> findById(Long messageId) {
        return messageDAO.findById(messageId);
    }
}
