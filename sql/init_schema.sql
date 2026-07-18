-- ============================================================
-- ChatBox_TheStars — Database Initialization Script
-- Target: SQL Server
-- ============================================================

-- Create database if not exists

USE ChatBox_TheStars;
GO

-- ============================================================
-- 1. USERS TABLE
-- Stores user information from Google OAuth 2.0
-- ============================================================
IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'Users')
BEGIN
    CREATE TABLE Users (
        id              BIGINT IDENTITY(1,1) PRIMARY KEY,
        google_id       NVARCHAR(255) NULL,
        email           NVARCHAR(255) NOT NULL UNIQUE,
        password        NVARCHAR(255) NULL,                     -- Password hash for local auth
        avatar          NVARCHAR(500),
        display_name    NVARCHAR(255) NOT NULL,
        team            NVARCHAR(100) NULL,                     -- IT, Marketing, Design
        role            NVARCHAR(50) NOT NULL DEFAULT 'USER',  -- USER, ADMIN
        last_login_ip   NVARCHAR(45),                           -- Supports IPv6
        status          NVARCHAR(20) NOT NULL DEFAULT 'OFFLINE', -- ONLINE, OFFLINE, AWAY
        created_at      DATETIME2 NOT NULL DEFAULT GETDATE(),
        updated_at      DATETIME2 NOT NULL DEFAULT GETDATE()
    );
END
GO

-- ============================================================
-- 2. CONVERSATIONS TABLE
-- Represents both 1-1 chats and group chats
-- ============================================================
IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'Conversations')
BEGIN
    CREATE TABLE Conversations (
        id              BIGINT IDENTITY(1,1) PRIMARY KEY,
        name            NVARCHAR(255),                          -- NULL for 1-1 chats
        type            NVARCHAR(10) NOT NULL DEFAULT 'SINGLE', -- SINGLE, GROUP
        avatar          NVARCHAR(500),                          -- Group avatar
        created_by      BIGINT,
        created_at      DATETIME2 NOT NULL DEFAULT GETDATE(),
        updated_at      DATETIME2 NOT NULL DEFAULT GETDATE(),

        CONSTRAINT FK_Conversations_CreatedBy
            FOREIGN KEY (created_by) REFERENCES Users(id)
    );
END
GO

-- ============================================================
-- 3. PARTICIPANTS TABLE
-- Junction table: Users <-> Conversations (many-to-many)
-- ============================================================
IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'Participants')
BEGIN
    CREATE TABLE Participants (
        id                  BIGINT IDENTITY(1,1) PRIMARY KEY,
        conversation_id     BIGINT NOT NULL,
        user_id             BIGINT NOT NULL,
        role                NVARCHAR(20) NOT NULL DEFAULT 'MEMBER', -- ADMIN, MEMBER
        joined_at           DATETIME2 NOT NULL DEFAULT GETDATE(),
        last_read_at        DATETIME2,                               -- Track unread messages

        CONSTRAINT FK_Participants_Conversation
            FOREIGN KEY (conversation_id) REFERENCES Conversations(id) ON DELETE CASCADE,
        CONSTRAINT FK_Participants_User
            FOREIGN KEY (user_id) REFERENCES Users(id) ON DELETE CASCADE,
        CONSTRAINT UQ_Participant_Unique
            UNIQUE (conversation_id, user_id)
    );
END
GO

-- ============================================================
-- 4. MESSAGES TABLE
-- Stores all chat messages
-- ============================================================
IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'Messages')
BEGIN
    CREATE TABLE Messages (
        id                  BIGINT IDENTITY(1,1) PRIMARY KEY,
        conversation_id     BIGINT NOT NULL,
        sender_id           BIGINT NOT NULL,
        content             NVARCHAR(MAX),
        message_type        NVARCHAR(20) NOT NULL DEFAULT 'TEXT',  -- TEXT, FILE, MEETING_LINK, IMAGE, SYSTEM
        is_deleted          BIT NOT NULL DEFAULT 0,
        created_at          DATETIME2 NOT NULL DEFAULT GETDATE(),

        CONSTRAINT FK_Messages_Conversation
            FOREIGN KEY (conversation_id) REFERENCES Conversations(id) ON DELETE CASCADE,
        CONSTRAINT FK_Messages_Sender
            FOREIGN KEY (sender_id) REFERENCES Users(id)
    );
END
GO

-- Threads: a reply points back at the message it's replying to.
-- Added via ALTER so this stays safe to re-run against an already-created Messages table.
IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('Messages') AND name = 'parent_id')
BEGIN
    ALTER TABLE Messages ADD parent_id BIGINT NULL;
END
GO

IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('Messages') AND name = 'reply_count')
BEGIN
    ALTER TABLE Messages ADD reply_count INT NOT NULL DEFAULT 0;
END
GO

IF NOT EXISTS (SELECT * FROM sys.foreign_keys WHERE name = 'FK_Messages_Parent')
BEGIN
    ALTER TABLE Messages ADD CONSTRAINT FK_Messages_Parent FOREIGN KEY (parent_id) REFERENCES Messages(id);
END
GO

-- ============================================================
-- 5. ATTACHMENTS TABLE
-- Stores file/image attachments linked to messages
-- ============================================================
IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'Attachments')
BEGIN
    CREATE TABLE Attachments (
        id              BIGINT IDENTITY(1,1) PRIMARY KEY,
        message_id      BIGINT NOT NULL,
        file_url        NVARCHAR(500) NOT NULL,
        file_name       NVARCHAR(255) NOT NULL,
        file_type       NVARCHAR(100) NOT NULL,                 -- MIME type: image/png, application/pdf
        file_size       BIGINT NOT NULL DEFAULT 0,              -- Size in bytes
        created_at      DATETIME2 NOT NULL DEFAULT GETDATE(),

        CONSTRAINT FK_Attachments_Message
            FOREIGN KEY (message_id) REFERENCES Messages(id) ON DELETE CASCADE
    );
END
GO

-- ============================================================
-- 6. MESSAGE REACTIONS TABLE
-- Emoji reactions on messages
-- ============================================================
IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'MessageReactions')
BEGIN
    CREATE TABLE MessageReactions (
        id              BIGINT IDENTITY(1,1) PRIMARY KEY,
        message_id      BIGINT NOT NULL,
        user_id         BIGINT NOT NULL,
        emoji           NVARCHAR(10) NOT NULL,                  -- Unicode emoji: 👍❤️😂😮😢😡
        created_at      DATETIME2 NOT NULL DEFAULT GETDATE(),

        CONSTRAINT FK_Reactions_Message
            FOREIGN KEY (message_id) REFERENCES Messages(id) ON DELETE CASCADE,
        CONSTRAINT FK_Reactions_User
            FOREIGN KEY (user_id) REFERENCES Users(id) ON DELETE CASCADE,
        CONSTRAINT UQ_Reaction_Unique
            UNIQUE (message_id, user_id, emoji)                 -- One emoji per user per message
    );
END
GO

-- ============================================================
-- 7. FRIENDSHIPS TABLE
-- Connections between two users: friend requests and accepted friendships.
-- One row per pair regardless of direction; status tracks its lifecycle.
-- Declined / cancelled / unfriended relationships are deleted, not stored.
-- ============================================================
IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'Friendships')
BEGIN
    CREATE TABLE Friendships (
        id              BIGINT IDENTITY(1,1) PRIMARY KEY,
        requester_id    BIGINT NOT NULL,                        -- User who sent the request
        addressee_id    BIGINT NOT NULL,                        -- User who received the request
        status          NVARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING, ACCEPTED
        created_at      DATETIME2 NOT NULL DEFAULT GETDATE(),
        updated_at      DATETIME2 NOT NULL DEFAULT GETDATE(),

        CONSTRAINT FK_Friendships_Requester
            FOREIGN KEY (requester_id) REFERENCES Users(id),
        CONSTRAINT FK_Friendships_Addressee
            FOREIGN KEY (addressee_id) REFERENCES Users(id),
        CONSTRAINT UQ_Friendship_Pair
            UNIQUE (requester_id, addressee_id),
        CONSTRAINT CHK_Friendship_NotSelf
            CHECK (requester_id <> addressee_id)
    );
END
GO

-- ============================================================
-- 8. MESSAGE READS TABLE
-- Per-recipient delivery/read tracking for tick-style receipts.
-- One row per (message, recipient) — the sender never gets a row for
-- their own message. delivered_at/read_at are set independently so a
-- message can be DELIVERED without being READ yet.
-- ============================================================
IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'Message_Reads')
BEGIN
    CREATE TABLE Message_Reads (
        message_id      BIGINT NOT NULL,
        user_id         BIGINT NOT NULL,
        delivered_at    DATETIME2 NULL,
        read_at         DATETIME2 NULL,

        CONSTRAINT PK_MessageReads PRIMARY KEY (message_id, user_id),
        CONSTRAINT FK_MessageReads_Message
            FOREIGN KEY (message_id) REFERENCES Messages(id) ON DELETE CASCADE,
        CONSTRAINT FK_MessageReads_User
            FOREIGN KEY (user_id) REFERENCES Users(id)
    );
END
GO

-- ============================================================
-- 9. NOTIFICATIONS TABLE
-- One row per @mention (or future notification type) a user should see.
-- ============================================================
IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'Notifications')
BEGIN
    CREATE TABLE Notifications (
        id              BIGINT IDENTITY(1,1) PRIMARY KEY,
        user_id         BIGINT NOT NULL,                    -- recipient
        message_id      BIGINT NOT NULL,                    -- the message that mentioned them
        content         NVARCHAR(255) NOT NULL,             -- preview text shown in the notification
        is_read         BIT NOT NULL DEFAULT 0,
        created_at      DATETIME2 NOT NULL DEFAULT GETDATE(),

        CONSTRAINT FK_Notifications_User
            FOREIGN KEY (user_id) REFERENCES Users(id) ON DELETE CASCADE,
        CONSTRAINT FK_Notifications_Message
            FOREIGN KEY (message_id) REFERENCES Messages(id)
    );
END
GO

-- ============================================================
-- 10. GROUP OWNERSHIP MODEL (OWNER / DEPUTY / MEMBER)
-- Migrates the old flat ADMIN/MEMBER roles to a Zalo-style model with
-- exactly one OWNER ("nhom truong") per group, plus zero or more DEPUTY
-- ("pho nhom") co-admins. Safe to re-run: once no Participants row has
-- role = 'ADMIN' in a GROUP conversation, both statements below no-op.
-- ============================================================
IF EXISTS (SELECT * FROM sys.tables WHERE name = 'Participants')
BEGIN
    -- Earliest-joined ADMIN per group becomes the OWNER.
    ;WITH RankedAdmins AS (
        SELECT p.id,
               ROW_NUMBER() OVER (PARTITION BY p.conversation_id ORDER BY p.joined_at ASC, p.id ASC) AS rn
        FROM Participants p
        INNER JOIN Conversations c ON c.id = p.conversation_id
        WHERE p.role = 'ADMIN' AND c.type = 'GROUP'
    )
    UPDATE Participants
    SET role = 'OWNER'
    WHERE id IN (SELECT id FROM RankedAdmins WHERE rn = 1);

    -- Any other pre-existing ADMIN in the same group becomes a DEPUTY.
    UPDATE p
    SET role = 'DEPUTY'
    FROM Participants p
    INNER JOIN Conversations c ON c.id = p.conversation_id
    WHERE p.role = 'ADMIN' AND c.type = 'GROUP';
END
GO

-- Allow dissolving a group (deleting its Conversations row) to cascade into
-- any Notifications referencing its messages, instead of being blocked by
-- an FK error — needed now that leaving as the last member deletes the group.
IF EXISTS (
    SELECT 1 FROM sys.foreign_keys
    WHERE name = 'FK_Notifications_Message' AND delete_referential_action = 0
)
BEGIN
    ALTER TABLE Notifications DROP CONSTRAINT FK_Notifications_Message;
    ALTER TABLE Notifications ADD CONSTRAINT FK_Notifications_Message
        FOREIGN KEY (message_id) REFERENCES Messages(id) ON DELETE CASCADE;
END
GO

-- ============================================================
-- INDEXES for query performance
-- ============================================================

-- Messages: Query by conversation (most common)
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'IX_Messages_ConversationId_CreatedAt')
    CREATE INDEX IX_Messages_ConversationId_CreatedAt
        ON Messages (conversation_id, created_at DESC);

-- Messages: Query by sender
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'IX_Messages_SenderId')
    CREATE INDEX IX_Messages_SenderId
        ON Messages (sender_id);

-- Participants: Find all conversations for a user
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'IX_Participants_UserId')
    CREATE INDEX IX_Participants_UserId
        ON Participants (user_id);

-- Participants: Find all users in a conversation
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'IX_Participants_ConversationId')
    CREATE INDEX IX_Participants_ConversationId
        ON Participants (conversation_id);

-- Attachments: Find attachments by message
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'IX_Attachments_MessageId')
    CREATE INDEX IX_Attachments_MessageId
        ON Attachments (message_id);

-- Reactions: Find reactions by message
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'IX_Reactions_MessageId')
    CREATE INDEX IX_Reactions_MessageId
        ON MessageReactions (message_id);

-- Users: Search by email or Google ID
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'IX_Users_Email')
    CREATE INDEX IX_Users_Email
        ON Users (email);

IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'IX_Users_GoogleId')
    CREATE UNIQUE NONCLUSTERED INDEX IX_Users_GoogleId
        ON Users (google_id)
        WHERE google_id IS NOT NULL;

-- Friendships: Find incoming/outgoing pending requests for a user
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'IX_Friendships_Addressee_Status')
    CREATE INDEX IX_Friendships_Addressee_Status
        ON Friendships (addressee_id, status);

IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'IX_Friendships_Requester_Status')
    CREATE INDEX IX_Friendships_Requester_Status
        ON Friendships (requester_id, status);

-- Message_Reads: Aggregate delivered/read state per message (tick rendering)
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'IX_MessageReads_Message')
    CREATE INDEX IX_MessageReads_Message
        ON Message_Reads (message_id);

-- Messages: Find all replies in a thread
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'IX_Messages_ParentId')
    CREATE INDEX IX_Messages_ParentId
        ON Messages (parent_id)
        WHERE parent_id IS NOT NULL;

-- Notifications: Find a user's unread notifications
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'IX_Notifications_User_Unread')
    CREATE INDEX IX_Notifications_User_Unread
        ON Notifications (user_id, is_read);

GO

PRINT '✅ ChatBox_TheStars database schema created successfully!';
GO
