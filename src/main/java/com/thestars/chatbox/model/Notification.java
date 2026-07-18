package com.thestars.chatbox.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * A notification for a user — currently only @mentions, but the shape is generic.
 * Maps to the [Notifications] table in SQL Server.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Notification {

    private Long id;
    private Long userId;
    private Long messageId;
    private String content;

    @Builder.Default
    private boolean read = false;

    private LocalDateTime createdAt;

    // --- Transient — filled in so the client can jump straight to the right chat ---
    private Long conversationId;
}
