package com.thestars.chatbox.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Represents a chat message.
 * Maps to the [Messages] table in SQL Server.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Message {

    private Long id;
    private Long conversationId;
    private Long senderId;
    private String content;

    /** TEXT, FILE, MEETING_LINK, IMAGE, SYSTEM */
    @Builder.Default
    private String messageType = "TEXT";

    @Builder.Default
    private boolean deleted = false;

    private LocalDateTime createdAt;

    /** Null for a normal message; the id of the message this one is quoting/replying to */
    private Long parentId;

    /** Legacy thread-view reply counter — no longer surfaced in the UI, kept for schema compatibility */
    @Builder.Default
    private int replyCount = 0;

    // --- Transient fields ---

    /** Sender's profile (loaded on demand) */
    private User sender;

    /** When parentId is set: the quoted message's sender name, for the inline reply preview */
    private String replyToSenderName;

    /** When parentId is set: the quoted message's content snippet, for the inline reply preview */
    private String replyToContent;

    /** Attachments linked to this message */
    private List<Attachment> attachments;

    /** Reactions on this message */
    private List<MessageReaction> reactions;

    /** Temporary client-side message ID for optimistic UI updates */
    private String clientMsgId;

    /** SENT, DELIVERED, or READ — only meaningful for the requesting user's own messages */
    private String readState;
}
