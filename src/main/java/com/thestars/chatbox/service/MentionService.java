package com.thestars.chatbox.service;

import com.thestars.chatbox.dao.ConversationDAO;
import com.thestars.chatbox.dao.NotificationDAO;
import com.thestars.chatbox.dao.ParticipantDAO;
import com.thestars.chatbox.dao.UserDAO;
import com.thestars.chatbox.model.Conversation;
import com.thestars.chatbox.model.Message;
import com.thestars.chatbox.model.User;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses @mentions out of a message and notifies whoever was mentioned.
 *
 * Handles are the sender's display name with spaces removed (so "Đỗ Khoa" is
 * mentioned as "@ĐỗKhoa") or their email's local part — either works, matching
 * whatever the mention-autocomplete dropdown inserted. Pattern.UNICODE_CHARACTER_CLASS
 * is required here: plain \w is ASCII-only and would silently fail to match the
 * very first character of most Vietnamese names.
 */
@Service
public class MentionService {

    private static final Pattern MENTION_PATTERN = Pattern.compile("@(\\w+)", Pattern.UNICODE_CHARACTER_CLASS);
    private static final int PREVIEW_LENGTH = 80;

    private final ParticipantDAO participantDAO;
    private final UserDAO userDAO;
    private final NotificationDAO notificationDAO;
    private final ConversationDAO conversationDAO;
    private final SimpMessagingTemplate messagingTemplate;

    public MentionService(ParticipantDAO participantDAO, UserDAO userDAO,
                          NotificationDAO notificationDAO, ConversationDAO conversationDAO,
                          SimpMessagingTemplate messagingTemplate) {
        this.participantDAO = participantDAO;
        this.userDAO = userDAO;
        this.notificationDAO = notificationDAO;
        this.conversationDAO = conversationDAO;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Find every @handle in the message that matches an actual participant of its
     * conversation, and notify each of them once (a name mentioned twice in the
     * same message only generates one notification).
     */
    public void processMentions(Message message) {
        Matcher matcher = MENTION_PATTERN.matcher(message.getContent());
        if (!matcher.find()) return;

        List<User> participants = participantDAO.findByConversationId(message.getConversationId()).stream()
                .map(p -> userDAO.findById(p.getUserId()))
                .flatMap(Optional::stream)
                .filter(u -> !u.getId().equals(message.getSenderId()))
                .toList();

        Set<Long> alreadyNotified = new HashSet<>();
        matcher.reset();
        while (matcher.find()) {
            String handle = matcher.group(1);
            participants.stream()
                    .filter(u -> matchesHandle(u, handle))
                    .findFirst()
                    .filter(u -> alreadyNotified.add(u.getId()))
                    .ifPresent(u -> notify(u, message));
        }
    }

    private boolean matchesHandle(User user, String handle) {
        String nameHandle = user.getDisplayName() != null ? user.getDisplayName().replace(" ", "") : "";
        String emailHandle = user.getEmail() != null ? user.getEmail().split("@")[0] : "";
        return nameHandle.equalsIgnoreCase(handle) || emailHandle.equalsIgnoreCase(handle);
    }

    private void notify(User target, Message message) {
        String senderName = message.getSender() != null ? message.getSender().getDisplayName() : "Someone";
        String preview = message.getContent().length() > PREVIEW_LENGTH
                ? message.getContent().substring(0, PREVIEW_LENGTH) + "…"
                : message.getContent();

        Optional<Conversation> conversation = conversationDAO.findById(message.getConversationId());
        boolean isGroup = conversation.map(c -> "GROUP".equals(c.getType())).orElse(false);
        String groupName = conversation.map(Conversation::getName).orElse("this group");

        String content = isGroup
                ? senderName + " mentioned you in " + groupName + ": \"" + preview + "\""
                : senderName + " mentioned you: \"" + preview + "\"";

        var saved = notificationDAO.create(target.getId(), message.getId(), content);
        saved.setConversationId(message.getConversationId());

        messagingTemplate.convertAndSendToUser(target.getEmail(), "/queue/notifications", Map.of(
                "id", saved.getId(),
                "content", content,
                "conversationId", message.getConversationId(),
                "createdAt", String.valueOf(System.currentTimeMillis())
        ));
    }
}
