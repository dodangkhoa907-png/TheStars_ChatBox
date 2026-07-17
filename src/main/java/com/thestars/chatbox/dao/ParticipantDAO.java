package com.thestars.chatbox.dao;

import com.thestars.chatbox.model.Participant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Data Access Object for the Participants table.
 */
@Repository
public class ParticipantDAO {

    private final JdbcTemplate jdbcTemplate;

    public ParticipantDAO(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<Participant> rowMapper = (rs, rowNum) -> Participant.builder()
            .id(rs.getLong("id"))
            .conversationId(rs.getLong("conversation_id"))
            .userId(rs.getLong("user_id"))
            .role(rs.getString("role"))
            .joinedAt(rs.getTimestamp("joined_at") != null ? rs.getTimestamp("joined_at").toLocalDateTime() : null)
            .lastReadAt(rs.getTimestamp("last_read_at") != null ? rs.getTimestamp("last_read_at").toLocalDateTime() : null)
            .build();

    /**
     * Add a user to a conversation.
     */
    public void addParticipant(Long conversationId, Long userId, String role) {
        String sql = """
            INSERT INTO Participants (conversation_id, user_id, role, joined_at)
            VALUES (?, ?, ?, GETDATE())
            """;
        jdbcTemplate.update(sql, conversationId, userId, role != null ? role : "MEMBER");
    }

    /**
     * Remove a user from a conversation.
     */
    public void removeParticipant(Long conversationId, Long userId) {
        String sql = "DELETE FROM Participants WHERE conversation_id = ? AND user_id = ?";
        jdbcTemplate.update(sql, conversationId, userId);
    }

    /**
     * Find all participants of a conversation.
     */
    public List<Participant> findByConversationId(Long conversationId) {
        String sql = "SELECT * FROM Participants WHERE conversation_id = ? ORDER BY joined_at";
        return jdbcTemplate.query(sql, rowMapper, conversationId);
    }

    /**
     * Find all conversations a user participates in (returns participant records).
     */
    public List<Participant> findByUserId(Long userId) {
        String sql = "SELECT * FROM Participants WHERE user_id = ?";
        return jdbcTemplate.query(sql, rowMapper, userId);
    }

    /**
     * Check if a user is a participant of a conversation.
     */
    public boolean isParticipant(Long conversationId, Long userId) {
        String sql = "SELECT COUNT(*) FROM Participants WHERE conversation_id = ? AND user_id = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, conversationId, userId);
        return count != null && count > 0;
    }

    /**
     * Update the last_read_at timestamp (mark messages as read).
     */
    public void updateLastRead(Long conversationId, Long userId) {
        String sql = "UPDATE Participants SET last_read_at = GETDATE() WHERE conversation_id = ? AND user_id = ?";
        jdbcTemplate.update(sql, conversationId, userId);
    }

    /**
     * Get the count of participants in a conversation.
     */
    public int countByConversationId(Long conversationId) {
        String sql = "SELECT COUNT(*) FROM Participants WHERE conversation_id = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, conversationId);
        return count != null ? count : 0;
    }
}
