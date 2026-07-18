package com.thestars.chatbox.dao;

import com.thestars.chatbox.model.Message;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Types;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object for the Messages table.
 */
@Repository
public class MessageDAO {

    private final JdbcTemplate jdbcTemplate;

    public MessageDAO(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<Message> rowMapper = (rs, rowNum) -> Message.builder()
            .id(rs.getLong("id"))
            .conversationId(rs.getLong("conversation_id"))
            .senderId(rs.getLong("sender_id"))
            .content(rs.getString("content"))
            .messageType(rs.getString("message_type"))
            .deleted(rs.getBoolean("is_deleted"))
            .createdAt(rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toLocalDateTime() : null)
            .parentId(rs.getObject("parent_id") != null ? rs.getLong("parent_id") : null)
            .replyCount(rs.getInt("reply_count"))
            .build();

    /**
     * Find a message by ID.
     */
    public Optional<Message> findById(Long id) {
        String sql = "SELECT * FROM Messages WHERE id = ?";
        List<Message> results = jdbcTemplate.query(sql, rowMapper, id);
        return results.stream().findFirst();
    }

    /**
     * Find top-level messages by conversation ID with pagination (newest first).
     * Thread replies (parent_id set) are excluded — they only ever show inside their thread.
     * @param conversationId the conversation
     * @param offset number of messages to skip
     * @param limit max messages to return
     */
    public List<Message> findByConversationId(Long conversationId, int offset, int limit) {
        String sql = """
            SELECT * FROM Messages
            WHERE conversation_id = ? AND is_deleted = 0 AND parent_id IS NULL
            ORDER BY created_at DESC
            OFFSET ? ROWS FETCH NEXT ? ROWS ONLY
            """;
        return jdbcTemplate.query(sql, rowMapper, conversationId, offset, limit);
    }

    /**
     * Find all replies in a thread, oldest first (chronological reading order).
     */
    public List<Message> findByParentId(Long parentId) {
        String sql = "SELECT * FROM Messages WHERE parent_id = ? AND is_deleted = 0 ORDER BY created_at ASC";
        return jdbcTemplate.query(sql, rowMapper, parentId);
    }

    /**
     * Bump a message's reply_count by one (called whenever a new thread reply is saved).
     */
    public void incrementReplyCount(Long parentId) {
        jdbcTemplate.update("UPDATE Messages SET reply_count = reply_count + 1 WHERE id = ?", parentId);
    }

    /**
     * Find the latest top-level message in a conversation (for sidebar preview).
     */
    public Optional<Message> findLatestByConversationId(Long conversationId) {
        String sql = """
            SELECT TOP 1 * FROM Messages
            WHERE conversation_id = ? AND is_deleted = 0 AND parent_id IS NULL
            ORDER BY created_at DESC
            """;
        List<Message> results = jdbcTemplate.query(sql, rowMapper, conversationId);
        return results.stream().findFirst();
    }

    /**
     * Save a new message and return it with the generated ID.
     */
    public Message save(Message message) {
        String sql = """
            INSERT INTO Messages (conversation_id, sender_id, content, message_type, parent_id, is_deleted, created_at)
            VALUES (?, ?, ?, ?, ?, 0, GETDATE())
            """;

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, message.getConversationId());
            ps.setLong(2, message.getSenderId());
            ps.setString(3, message.getContent());
            ps.setString(4, message.getMessageType() != null ? message.getMessageType() : "TEXT");
            if (message.getParentId() != null) {
                ps.setLong(5, message.getParentId());
            } else {
                ps.setNull(5, Types.BIGINT);
            }
            return ps;
        }, keyHolder);

        Number generatedId = keyHolder.getKey();
        if (generatedId != null) {
            message.setId(generatedId.longValue());
        }
        return message;
    }

    /**
     * Soft-delete a message.
     */
    public void softDelete(Long messageId) {
        String sql = "UPDATE Messages SET is_deleted = 1 WHERE id = ?";
        jdbcTemplate.update(sql, messageId);
    }

    /**
     * Null out thread parent_id references within a conversation. Messages.parent_id
     * self-references Messages(id) with no cascade, so this must run before a
     * conversation delete cascades into Messages — otherwise a reply row still
     * pointing at its (about to be deleted) parent would violate that FK.
     */
    public void clearThreadParents(Long conversationId) {
        String sql = "UPDATE Messages SET parent_id = NULL WHERE conversation_id = ?";
        jdbcTemplate.update(sql, conversationId);
    }

    /**
     * Count unread top-level messages for a user in a conversation.
     * Compares message timestamps against the participant's last_read_at.
     */
    public int countUnread(Long conversationId, Long userId) {
        String sql = """
            SELECT COUNT(*)
            FROM Messages m
            WHERE m.conversation_id = ?
              AND m.sender_id != ?
              AND m.is_deleted = 0
              AND m.parent_id IS NULL
              AND m.created_at > COALESCE(
                  (SELECT p.last_read_at FROM Participants p
                   WHERE p.conversation_id = ? AND p.user_id = ?),
                  '1970-01-01')
            """;
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class,
                conversationId, userId, conversationId, userId);
        return count != null ? count : 0;
    }

    /**
     * Count total top-level messages in a conversation.
     */
    public int countByConversationId(Long conversationId) {
        String sql = "SELECT COUNT(*) FROM Messages WHERE conversation_id = ? AND is_deleted = 0 AND parent_id IS NULL";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, conversationId);
        return count != null ? count : 0;
    }
}
