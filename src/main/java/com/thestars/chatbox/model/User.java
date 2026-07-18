package com.thestars.chatbox.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Represents a user authenticated via Google OAuth 2.0.
 * Maps to the [Users] table in SQL Server.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    private Long id;
    private String googleId;
    private String email;

    /** Never serialized to JSON — this is a bcrypt hash, and every endpoint that
     *  returns a User object (friends, search, profiles, message senders...) was
     *  otherwise shipping it straight to any authenticated client. */
    @JsonIgnore
    private String password;
    private String avatar;
    private String displayName;
    private String team;

    /** Role within the system: USER or ADMIN */
    @Builder.Default
    private String role = "USER";

    /** Last known IP address for session tracking */
    private String lastLoginIp;

    /** Real-time status: ONLINE, OFFLINE, AWAY */
    @Builder.Default
    private String status = "OFFLINE";

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
