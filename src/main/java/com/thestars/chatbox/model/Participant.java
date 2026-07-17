package com.thestars.chatbox.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Represents a user's participation in a conversation.
 * Maps to the [Participants] table in SQL Server.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Participant {

    private Long id;
    private Long conversationId;
    private Long userId;

    /** Role within the conversation: ADMIN or MEMBER */
    @Builder.Default
    private String role = "MEMBER";

    private LocalDateTime joinedAt;

    /** Timestamp of the last message the user has read in this conversation */
    private LocalDateTime lastReadAt;

    // --- Transient ---
    private User user;
}
