package com.thestars.chatbox.dao;

import com.thestars.chatbox.model.Conversation;
import com.thestars.chatbox.model.Message;
import com.thestars.chatbox.model.User;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object for the Conversations table.
 */
@Repository
public class ConversationDAO {

    private final JdbcTemplate jdbcTemplate;

    public ConversationDAO(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<Conversation> rowMapper = (rs, rowNum) -> Conversation.builder()
            .id(rs.getLong("id"))
            .name(rs.getString("name"))
            .type(rs.getString("type"))
            .avatar(rs.getString("avatar"))
            .createdBy(rs.getObject("created_by") != null ? rs.getLong("created_by") : null)
            .createdAt(rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toLocalDateTime() : null)
            .updatedAt(rs.getTimestamp("updated_at") != null ? rs.getTimestamp("updated_at").toLocalDateTime() : null)
            .build();

    /**
     * Find a conversation by ID.
     */
    public Optional<Conversation> findById(Long id) {
        String sql = "SELECT * FROM Conversations WHERE id = ?";
        List<Conversation> results = jdbcTemplate.query(sql, rowMapper, id);
        return results.stream().findFirst();
    }

    /**
     * Find all conversations that a user participates in,
     * ordered by the most recent message activity.
     */
    public List<Conversation> findByUserId(Long userId) {
        String sql = """
            SELECT c.*
            FROM Conversations c
            INNER JOIN Participants p ON c.id = p.conversation_id
            WHERE p.user_id = ?
            ORDER BY c.updated_at DESC
            """;
        return jdbcTemplate.query(sql, rowMapper, userId);
    }

    /**
     * Find all conversations that a user participates in with full details loaded
     * (unread count, last message, sender, other participant info for 1-1 chats)
     * in a single optimized query.
     */
    public List<Conversation> findByUserIdWithDetails(Long userId) {
        String sql = """
            SELECT 
                c.id AS conv_id, 
                c.name AS conv_name, 
                c.type AS conv_type, 
                c.avatar AS conv_avatar, 
                c.created_by AS conv_created_by, 
                c.created_at AS conv_created_at, 
                c.updated_at AS conv_updated_at,
                -- Last message fields
                lm.id AS last_msg_id, 
                lm.content AS last_msg_content, 
                lm.message_type AS last_msg_type, 
                lm.created_at AS last_msg_created_at, 
                lm.sender_id AS last_msg_sender_id,
                lmu.display_name AS last_msg_sender_name, 
                lmu.avatar AS last_msg_sender_avatar,
                -- Other participant fields (for SINGLE conversations)
                other_u.id AS other_user_id, 
                other_u.display_name AS other_user_display_name, 
                other_u.avatar AS other_user_avatar, 
                other_u.status AS other_user_status,
                -- Unread count (top-level messages only — thread replies don't surface here)
                (SELECT COUNT(*) FROM Messages m
                 WHERE m.conversation_id = c.id
                   AND m.sender_id != ?
                   AND m.is_deleted = 0
                   AND m.parent_id IS NULL
                   AND m.created_at > COALESCE(p.last_read_at, '1970-01-01')) AS unread_count
            FROM Conversations c
            INNER JOIN Participants p ON c.id = p.conversation_id
            OUTER APPLY (
                SELECT TOP 1 m2.id, m2.content, m2.message_type, m2.created_at, m2.sender_id
                FROM Messages m2
                WHERE m2.conversation_id = c.id AND m2.is_deleted = 0 AND m2.parent_id IS NULL
                ORDER BY m2.created_at DESC
            ) lm
            LEFT JOIN Users lmu ON lm.sender_id = lmu.id
            LEFT JOIN Participants other_p ON c.id = other_p.conversation_id AND c.type = 'SINGLE' AND other_p.user_id != ?
            LEFT JOIN Users other_u ON other_p.user_id = other_u.id
            WHERE p.user_id = ?
            ORDER BY c.updated_at DESC
            """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            Conversation conv = Conversation.builder()
                    .id(rs.getLong("conv_id"))
                    .name(rs.getString("conv_name"))
                    .type(rs.getString("conv_type"))
                    .avatar(rs.getString("conv_avatar"))
                    .createdBy(rs.getObject("conv_created_by") != null ? rs.getLong("conv_created_by") : null)
                    .createdAt(rs.getTimestamp("conv_created_at") != null ? rs.getTimestamp("conv_created_at").toLocalDateTime() : null)
                    .updatedAt(rs.getTimestamp("conv_updated_at") != null ? rs.getTimestamp("conv_updated_at").toLocalDateTime() : null)
                    .unreadCount(rs.getInt("unread_count"))
                    .build();

            // Populate last message
            Long lastMsgId = rs.getObject("last_msg_id") != null ? rs.getLong("last_msg_id") : null;
            if (lastMsgId != null) {
                User sender = User.builder()
                        .id(rs.getLong("last_msg_sender_id"))
                        .displayName(rs.getString("last_msg_sender_name"))
                        .avatar(rs.getString("last_msg_sender_avatar"))
                        .build();

                Message lastMsg = Message.builder()
                        .id(lastMsgId)
                        .conversationId(conv.getId())
                        .senderId(sender.getId())
                        .content(rs.getString("last_msg_content"))
                        .messageType(rs.getString("last_msg_type"))
                        .createdAt(rs.getTimestamp("last_msg_created_at") != null ? rs.getTimestamp("last_msg_created_at").toLocalDateTime() : null)
                        .sender(sender)
                        .build();

                conv.setLastMessage(lastMsg);
            }

            // Populate other participant info for SINGLE type
            if ("SINGLE".equals(conv.getType())) {
                Long otherUserId = rs.getObject("other_user_id") != null ? rs.getLong("other_user_id") : null;
                if (otherUserId != null) {
                    conv.setOtherUserId(otherUserId);
                    conv.setName(rs.getString("other_user_display_name"));
                    conv.setAvatar(rs.getString("other_user_avatar"));
                    conv.setOtherUserStatus(rs.getString("other_user_status"));
                }
            }

            return conv;
        }, userId, userId, userId);
    }

    /**
     * Find an existing 1-1 (SINGLE) conversation between two users.
     */
    public Optional<Conversation> findSingleConversation(Long userId1, Long userId2) {
        String sql = """
            SELECT c.*
            FROM Conversations c
            WHERE c.type = 'SINGLE'
              AND c.id IN (
                  SELECT p1.conversation_id
                  FROM Participants p1
                  INNER JOIN Participants p2 ON p1.conversation_id = p2.conversation_id
                  WHERE p1.user_id = ? AND p2.user_id = ?
              )
            """;
        List<Conversation> results = jdbcTemplate.query(sql, rowMapper, userId1, userId2);
        return results.stream().findFirst();
    }

    /**
     * Create a new conversation and return the generated ID.
     */
    public Conversation create(Conversation conversation) {
        String sql = """
            INSERT INTO Conversations (name, type, avatar, created_by, created_at, updated_at)
            VALUES (?, ?, ?, ?, GETDATE(), GETDATE())
            """;

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, conversation.getName());
            ps.setString(2, conversation.getType() != null ? conversation.getType() : "SINGLE");
            ps.setString(3, conversation.getAvatar());
            if (conversation.getCreatedBy() != null) {
                ps.setLong(4, conversation.getCreatedBy());
            } else {
                ps.setNull(4, java.sql.Types.BIGINT);
            }
            return ps;
        }, keyHolder);

        Number generatedId = keyHolder.getKey();
        if (generatedId != null) {
            conversation.setId(generatedId.longValue());
        }
        return conversation;
    }

    /**
     * Update conversation metadata (name, avatar).
     */
    public void update(Conversation conversation) {
        String sql = "UPDATE Conversations SET name = ?, avatar = ?, updated_at = GETDATE() WHERE id = ?";
        jdbcTemplate.update(sql, conversation.getName(), conversation.getAvatar(), conversation.getId());
    }

    /**
     * Touch the updated_at timestamp (called when a new message is sent).
     */
    public void touch(Long conversationId) {
        String sql = "UPDATE Conversations SET updated_at = GETDATE() WHERE id = ?";
        jdbcTemplate.update(sql, conversationId);
    }

    /**
     * Find all GROUP conversations that a user does NOT participate in.
     */
    public List<Conversation> findAvailableGroups(Long userId) {
        String sql = """
            SELECT c.*
            FROM Conversations c
            WHERE c.type = 'GROUP'
              AND c.id NOT IN (
                  SELECT p.conversation_id
                  FROM Participants p
                  WHERE p.user_id = ?
              )
            ORDER BY c.name
            """;
        return jdbcTemplate.query(sql, rowMapper, userId);
    }

    /**
     * Delete a conversation.
     */
    public void delete(Long id) {
        jdbcTemplate.update("DELETE FROM Conversations WHERE id = ?", id);
    }
}
