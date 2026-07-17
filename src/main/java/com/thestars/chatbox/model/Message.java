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

    /** TEXT, FILE, MEETING_LINK, IMAGE */
    @Builder.Default
    private String messageType = "TEXT";

    @Builder.Default
    private boolean deleted = false;

    private LocalDateTime createdAt;

    // --- Transient fields ---

    /** Sender's profile (loaded on demand) */
    private User sender;

    /** Attachments linked to this message */
    private List<Attachment> attachments;

    /** Reactions on this message */
    private List<MessageReaction> reactions;

    /** Temporary client-side message ID for optimistic UI updates */
    private String clientMsgId;
}
