package com.thestars.chatbox.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Represents a friend connection (or pending request) between two users.
 * Maps to the [Friendships] table in SQL Server.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Friendship {

    private Long id;
    private Long requesterId;
    private Long addresseeId;

    /** PENDING or ACCEPTED — declined/cancelled/unfriended rows are deleted, not stored. */
    @Builder.Default
    private String status = "PENDING";

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // --- Transient ---
    private User requester;
    private User addressee;
}
