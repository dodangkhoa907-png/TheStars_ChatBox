package com.thestars.chatbox.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Represents an emoji reaction on a message.
 * Maps to the [MessageReactions] table in SQL Server.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageReaction {

    private Long id;
    private Long messageId;
    private Long userId;

    /** Unicode emoji character: 👍❤️😂😮😢😡 */
    private String emoji;

    private LocalDateTime createdAt;

    // --- Transient ---
    private User user;
}
