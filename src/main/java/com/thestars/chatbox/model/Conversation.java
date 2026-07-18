package com.thestars.chatbox.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Represents a conversation (1-1 or group chat).
 * Maps to the [Conversations] table in SQL Server.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Conversation {

    private Long id;

    /** Display name (null for SINGLE type — uses other participant's name) */
    private String name;

    /** SINGLE = 1-to-1 chat, GROUP = group chat */
    @Builder.Default
    private String type = "SINGLE";

    /** Group avatar URL */
    private String avatar;

    /** ID of the user who created this conversation */
    private Long createdBy;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // --- Transient fields (not in DB, populated at runtime) ---

    /** Last message preview for sidebar display */
    private Message lastMessage;

    /** Number of unread messages for the current user */
    private int unreadCount;

    /** List of participants with their role, loaded on demand */
    private java.util.List<Participant> participants;

    /** Whether the requesting user is an OWNER or DEPUTY of this conversation (set per-request, not persisted) */
    private boolean currentUserIsAdmin;

    /** The requesting user's exact role in this conversation: OWNER, DEPUTY, or MEMBER (null for SINGLE chats) */
    private String currentUserRole;

    /** For SINGLE conversations: the other participant's user id (used to match live presence events). */
    private Long otherUserId;

    /** For SINGLE conversations: the other participant's live status (ONLINE/AWAY/OFFLINE). */
    private String otherUserStatus;
}
