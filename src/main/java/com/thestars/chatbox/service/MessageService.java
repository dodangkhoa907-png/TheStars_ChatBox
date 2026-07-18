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
    private final MessageReadDAO messageReadDAO;
    private final MentionService mentionService;

    public MessageService(MessageDAO messageDAO, AttachmentDAO attachmentDAO,
                          MessageReactionDAO reactionDAO, ConversationDAO conversationDAO,
                          UserDAO userDAO, MessageReadDAO messageReadDAO, MentionService mentionService) {
        this.messageDAO = messageDAO;
        this.attachmentDAO = attachmentDAO;
        this.reactionDAO = reactionDAO;
        this.conversationDAO = conversationDAO;
        this.userDAO = userDAO;
        this.messageReadDAO = messageReadDAO;
        this.mentionService = mentionService;
    }

    /**
     * Get paginated messages for a conversation with sender info, attachments, reactions,
     * and — for the requesting user's own messages — their delivered/read tick state.
     */
    public List<Message> getMessages(Long conversationId, int page, int size, Long requestingUserId) {
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

        // Tick state only matters for messages the requester sent themselves
        List<Long> ownMessageIds = messages.stream()
                .filter(m -> m.getSenderId().equals(requestingUserId))
                .map(Message::getId)
                .toList();
        Map<Long, String> readStates = messageReadDAO.getReadStates(ownMessageIds);

        // Enrich messages
        for (Message msg : messages) {
            msg.setSender(senderMap.get(msg.getSenderId()));
            msg.setReactions(reactionMap.getOrDefault(msg.getId(), List.of()));

            if (msg.getSenderId().equals(requestingUserId)) {
                msg.setReadState(readStates.getOrDefault(msg.getId(), "SENT"));
            }

            // Load attachments for FILE/IMAGE messages
            if ("FILE".equals(msg.getMessageType()) || "IMAGE".equals(msg.getMessageType())) {
                msg.setAttachments(attachmentDAO.findByMessageId(msg.getId()));
            }
        }

        return messages;
    }

    /**
     * Record that a recipient's client has received a message live over the socket.
     * Returns the message's conversation id so the caller can broadcast a tick update.
     */
    public Long markDelivered(Long messageId, Long userId) {
        Optional<Message> message = messageDAO.findById(messageId);
        if (message.isEmpty()) return null;

        messageReadDAO.markDelivered(messageId, userId);
        return message.get().getConversationId();
    }

    /**
     * Send a new top-level message.
     */
    @Transactional
    public Message sendMessage(Long conversationId, Long senderId, String content, String messageType) {
        return sendMessage(conversationId, senderId, content, messageType, null);
    }

    /**
     * Send a message, optionally as a reply within an existing thread. Looks the
     * sender up by id — prefer the {@link #sendMessage(Long, User, String, String, Long)}
     * overload when the caller already has the User on hand (e.g. a WebSocket
     * handler that already resolved the Principal), to skip a redundant query.
     */
    @Transactional
    public Message sendMessage(Long conversationId, Long senderId, String content, String messageType, Long parentId) {
        return saveMessage(conversationId, userDAO.findById(senderId).orElse(null), senderId, content, messageType, parentId);
    }

    /**
     * Send a message with an already-resolved sender — the hot path for real-time
     * WebSocket sends, where {@code Principal} was already turned into a
     * {@code User} before this is called, so there's no need to fetch it again.
     */
    @Transactional
    public Message sendMessage(Long conversationId, User sender, String content, String messageType, Long parentId) {
        return saveMessage(conversationId, sender, sender.getId(), content, messageType, parentId);
    }

    private Message saveMessage(Long conversationId, User sender, Long senderId, String content, String messageType, Long parentId) {
        Message message = Message.builder()
                .conversationId(conversationId)
                .senderId(senderId)
                .content(content)
                .messageType(messageType != null ? messageType : "TEXT")
                .parentId(parentId)
                .build();

        message = messageDAO.save(message);

        // Touch conversation updated_at to reorder in sidebar
        conversationDAO.touch(conversationId);

        if (parentId != null) {
            messageDAO.incrementReplyCount(parentId);
        }

        message.setSender(sender);

        if ("TEXT".equals(message.getMessageType()) && content != null && content.contains("@")) {
            mentionService.processMentions(message);
        }

        return message;
    }

    /**
     * Reply to an existing message within its thread.
     */
    public Message replyInThread(Long parentId, User sender, String content) {
        Message parent = messageDAO.findById(parentId)
                .orElseThrow(() -> new IllegalArgumentException("Parent message not found"));
        return sendMessage(parent.getConversationId(), sender, content, "TEXT", parentId);
    }

    /**
     * Get all replies in a thread, enriched with sender info, oldest first.
     */
    public List<Message> getThreadReplies(Long parentId) {
        List<Message> replies = messageDAO.findByParentId(parentId);
        if (replies.isEmpty()) return replies;

        List<Long> senderIds = replies.stream().map(Message::getSenderId).distinct().toList();
        Map<Long, User> senderMap = userDAO.findByIds(senderIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        replies.forEach(r -> r.setSender(senderMap.get(r.getSenderId())));

        return replies;
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
