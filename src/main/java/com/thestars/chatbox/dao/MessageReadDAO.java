package com.thestars.chatbox.dao;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Data Access Object for the Message_Reads table — per-recipient
 * delivered/read tracking that backs the 4-state tick UI.
 */
@Repository
public class MessageReadDAO {

    private final JdbcTemplate jdbcTemplate;

    public MessageReadDAO(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Record that a recipient's client has received a message over the socket.
     * A no-op if this recipient already has a delivered (or read) row.
     */
    public void markDelivered(Long messageId, Long userId) {
        int updated = jdbcTemplate.update("""
                UPDATE Message_Reads SET delivered_at = COALESCE(delivered_at, GETDATE())
                WHERE message_id = ? AND user_id = ?
                """, messageId, userId);

        if (updated == 0) {
            jdbcTemplate.update("""
                    INSERT INTO Message_Reads (message_id, user_id, delivered_at)
                    VALUES (?, ?, GETDATE())
                    """, messageId, userId);
        }
    }

    /**
     * Mark every message in a conversation not sent by this user as delivered + read,
     * as of now. Called when the user opens/refreshes the conversation.
     */
    public void markConversationRead(Long conversationId, Long userId) {
        jdbcTemplate.update("""
                UPDATE mr SET
                    read_at = COALESCE(mr.read_at, GETDATE()),
                    delivered_at = COALESCE(mr.delivered_at, GETDATE())
                FROM Message_Reads mr
                INNER JOIN Messages m ON mr.message_id = m.id
                WHERE m.conversation_id = ? AND m.sender_id != ? AND mr.user_id = ?
                """, conversationId, userId, userId);

        jdbcTemplate.update("""
                INSERT INTO Message_Reads (message_id, user_id, delivered_at, read_at)
                SELECT m.id, ?, GETDATE(), GETDATE()
                FROM Messages m
                WHERE m.conversation_id = ? AND m.sender_id != ? AND m.is_deleted = 0
                  AND NOT EXISTS (
                      SELECT 1 FROM Message_Reads mr WHERE mr.message_id = m.id AND mr.user_id = ?
                  )
                """, userId, conversationId, userId, userId);
    }

    /**
     * Aggregate read state per message: READ if any recipient has read it, else
     * DELIVERED if any recipient has it, else absent (caller should default to SENT).
     */
    public Map<Long, String> getReadStates(List<Long> messageIds) {
        Map<Long, String> result = new HashMap<>();
        if (messageIds.isEmpty()) return result;

        String placeholders = String.join(",", messageIds.stream().map(id -> "?").toList());
        String sql = """
                SELECT message_id,
                       MAX(CASE WHEN read_at IS NOT NULL THEN 1 ELSE 0 END) AS any_read,
                       MAX(CASE WHEN delivered_at IS NOT NULL THEN 1 ELSE 0 END) AS any_delivered
                FROM Message_Reads
                WHERE message_id IN (%s)
                GROUP BY message_id
                """.formatted(placeholders);

        jdbcTemplate.query(sql, rs -> {
            long id = rs.getLong("message_id");
            boolean anyRead = rs.getInt("any_read") == 1;
            boolean anyDelivered = rs.getInt("any_delivered") == 1;
            result.put(id, anyRead ? "READ" : (anyDelivered ? "DELIVERED" : "SENT"));
        }, messageIds.toArray());

        return result;
    }
}
