package com.thestars.chatbox.dao;

import com.thestars.chatbox.model.MessageReaction;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Data Access Object for the MessageReactions table.
 */
@Repository
public class MessageReactionDAO {

    private final JdbcTemplate jdbcTemplate;

    public MessageReactionDAO(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<MessageReaction> rowMapper = (rs, rowNum) -> MessageReaction.builder()
            .id(rs.getLong("id"))
            .messageId(rs.getLong("message_id"))
            .userId(rs.getLong("user_id"))
            .emoji(rs.getString("emoji"))
            .createdAt(rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toLocalDateTime() : null)
            .build();

    /**
     * Add a reaction to a message. Uses MERGE to toggle (insert if not exists).
     */
    public void addReaction(Long messageId, Long userId, String emoji) {
        // Check if already reacted with same emoji → remove it (toggle behavior)
        String checkSql = "SELECT COUNT(*) FROM MessageReactions WHERE message_id = ? AND user_id = ? AND emoji = ?";
        Integer count = jdbcTemplate.queryForObject(checkSql, Integer.class, messageId, userId, emoji);

        if (count != null && count > 0) {
            // Toggle off
            removeReaction(messageId, userId, emoji);
        } else {
            // Add reaction
            String sql = """
                INSERT INTO MessageReactions (message_id, user_id, emoji, created_at)
                VALUES (?, ?, ?, GETDATE())
                """;
            jdbcTemplate.update(sql, messageId, userId, emoji);
        }
    }

    /**
     * Remove a specific reaction.
     */
    public void removeReaction(Long messageId, Long userId, String emoji) {
        String sql = "DELETE FROM MessageReactions WHERE message_id = ? AND user_id = ? AND emoji = ?";
        jdbcTemplate.update(sql, messageId, userId, emoji);
    }

    /**
     * Find all reactions for a message.
     */
    public List<MessageReaction> findByMessageId(Long messageId) {
        String sql = "SELECT * FROM MessageReactions WHERE message_id = ? ORDER BY created_at";
        return jdbcTemplate.query(sql, rowMapper, messageId);
    }

    /**
     * Find all reactions for multiple messages (batch load for efficiency).
     */
    public List<MessageReaction> findByMessageIds(List<Long> messageIds) {
        if (messageIds == null || messageIds.isEmpty()) return List.of();
        String placeholders = String.join(",", messageIds.stream().map(id -> "?").toList());
        String sql = "SELECT * FROM MessageReactions WHERE message_id IN (" + placeholders + ") ORDER BY created_at";
        return jdbcTemplate.query(sql, rowMapper, messageIds.toArray());
    }
}
