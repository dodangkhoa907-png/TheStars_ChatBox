package com.thestars.chatbox.dao;

import com.thestars.chatbox.model.Notification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;

/**
 * Data Access Object for the Notifications table.
 */
@Repository
public class NotificationDAO {

    private final JdbcTemplate jdbcTemplate;

    public NotificationDAO(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<Notification> rowMapper = (rs, rowNum) -> Notification.builder()
            .id(rs.getLong("id"))
            .userId(rs.getLong("user_id"))
            .messageId(rs.getLong("message_id"))
            .content(rs.getString("content"))
            .read(rs.getBoolean("is_read"))
            .createdAt(rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toLocalDateTime() : null)
            .build();

    /**
     * Create a new notification and return it with its generated ID.
     */
    public Notification create(Long userId, Long messageId, String content) {
        String sql = """
            INSERT INTO Notifications (user_id, message_id, content, is_read, created_at)
            VALUES (?, ?, ?, 0, GETDATE())
            """;

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, userId);
            ps.setLong(2, messageId);
            ps.setString(3, content);
            return ps;
        }, keyHolder);

        Number generatedId = keyHolder.getKey();
        Notification notification = Notification.builder()
                .userId(userId).messageId(messageId).content(content).read(false)
                .build();
        if (generatedId != null) {
            notification.setId(generatedId.longValue());
        }
        return notification;
    }

    /**
     * Most recent notifications for a user, newest first — collapsed to one row per
     * conversation (the latest mention in that conversation) so repeated mentions
     * from the same group don't flood the panel with duplicate entries.
     */
    public List<Notification> findRecentByUser(Long userId, int limit) {
        String sql = """
            SELECT TOP (?) id, user_id, message_id, content, is_read, created_at FROM (
                SELECT n.id, n.user_id, n.message_id, n.content, n.is_read, n.created_at,
                       ROW_NUMBER() OVER (PARTITION BY m.conversation_id ORDER BY n.created_at DESC) AS rn
                FROM Notifications n
                INNER JOIN Messages m ON m.id = n.message_id
                WHERE n.user_id = ?
            ) ranked
            WHERE rn = 1
            ORDER BY created_at DESC
            """;
        return jdbcTemplate.query(sql, rowMapper, limit, userId);
    }

    /**
     * Count of conversations with at least one unread mention — matches what the
     * (collapsed) notification list actually shows, rather than raw mention rows.
     */
    public int countUnread(Long userId) {
        String sql = """
            SELECT COUNT(DISTINCT m.conversation_id)
            FROM Notifications n
            INNER JOIN Messages m ON m.id = n.message_id
            WHERE n.user_id = ? AND n.is_read = 0
            """;
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, userId);
        return count != null ? count : 0;
    }

    /**
     * Mark a notification read — and since the panel only ever shows one collapsed
     * entry per conversation, this clears every mention notification in that same
     * conversation for the user, not just the single row that was clicked.
     */
    public void markRead(Long id, Long userId) {
        List<Long> conversationId = jdbcTemplate.query("""
            SELECT m.conversation_id AS conversation_id
            FROM Notifications n
            INNER JOIN Messages m ON m.id = n.message_id
            WHERE n.id = ? AND n.user_id = ?
            """, (rs, rowNum) -> rs.getLong("conversation_id"), id, userId);

        if (conversationId.isEmpty()) return;

        jdbcTemplate.update("""
            UPDATE n SET is_read = 1
            FROM Notifications n
            INNER JOIN Messages m ON m.id = n.message_id
            WHERE n.user_id = ? AND m.conversation_id = ?
            """, userId, conversationId.get(0));
    }

    public void markAllRead(Long userId) {
        jdbcTemplate.update("UPDATE Notifications SET is_read = 1 WHERE user_id = ? AND is_read = 0", userId);
    }
}
