package com.thestars.chatbox.dao;

import com.thestars.chatbox.model.Conversation;
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
