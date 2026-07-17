package com.thestars.chatbox.dao;

import com.thestars.chatbox.model.Friendship;
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
 * Data Access Object for the Friendships table.
 */
@Repository
public class FriendshipDAO {

    private final JdbcTemplate jdbcTemplate;

    public FriendshipDAO(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<Friendship> rowMapper = (rs, rowNum) -> Friendship.builder()
            .id(rs.getLong("id"))
            .requesterId(rs.getLong("requester_id"))
            .addresseeId(rs.getLong("addressee_id"))
            .status(rs.getString("status"))
            .createdAt(rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toLocalDateTime() : null)
            .updatedAt(rs.getTimestamp("updated_at") != null ? rs.getTimestamp("updated_at").toLocalDateTime() : null)
            .build();

    /**
     * Find the relationship row between two users, regardless of who sent the request.
     */
    public Optional<Friendship> findBetween(Long userId1, Long userId2) {
        String sql = """
            SELECT * FROM Friendships
            WHERE (requester_id = ? AND addressee_id = ?)
               OR (requester_id = ? AND addressee_id = ?)
            """;
        List<Friendship> results = jdbcTemplate.query(sql, rowMapper, userId1, userId2, userId2, userId1);
        return results.stream().findFirst();
    }

    public Optional<Friendship> findById(Long id) {
        String sql = "SELECT * FROM Friendships WHERE id = ?";
        List<Friendship> results = jdbcTemplate.query(sql, rowMapper, id);
        return results.stream().findFirst();
    }

    /**
     * Create a new PENDING friend request and return the persisted row.
     */
    public Friendship create(Long requesterId, Long addresseeId) {
        String sql = """
            INSERT INTO Friendships (requester_id, addressee_id, status, created_at, updated_at)
            VALUES (?, ?, 'PENDING', GETDATE(), GETDATE())
            """;

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, requesterId);
            ps.setLong(2, addresseeId);
            return ps;
        }, keyHolder);

        return findById(keyHolder.getKey().longValue()).orElseThrow();
    }

    public void updateStatus(Long id, String status) {
        String sql = "UPDATE Friendships SET status = ?, updated_at = GETDATE() WHERE id = ?";
        jdbcTemplate.update(sql, status, id);
    }

    public void deleteById(Long id) {
        String sql = "DELETE FROM Friendships WHERE id = ?";
        jdbcTemplate.update(sql, id);
    }

    /**
     * Pending requests where the given user is the recipient.
     */
    public List<Friendship> findIncomingPending(Long userId) {
        String sql = "SELECT * FROM Friendships WHERE addressee_id = ? AND status = 'PENDING' ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, rowMapper, userId);
    }

    /**
     * Pending requests the given user has sent and are awaiting a response.
     */
    public List<Friendship> findOutgoingPending(Long userId) {
        String sql = "SELECT * FROM Friendships WHERE requester_id = ? AND status = 'PENDING' ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, rowMapper, userId);
    }

    /**
     * Accepted friendships involving the given user, in either direction.
     */
    public List<Friendship> findAccepted(Long userId) {
        String sql = """
            SELECT * FROM Friendships
            WHERE (requester_id = ? OR addressee_id = ?) AND status = 'ACCEPTED'
            ORDER BY updated_at DESC
            """;
        return jdbcTemplate.query(sql, rowMapper, userId, userId);
    }
}
