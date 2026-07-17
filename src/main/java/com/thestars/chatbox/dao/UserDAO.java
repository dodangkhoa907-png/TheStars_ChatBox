package com.thestars.chatbox.dao;

import com.thestars.chatbox.model.User;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object for the Users table.
 * Uses JdbcTemplate for all database operations.
 */
@Repository
public class UserDAO {

    private final JdbcTemplate jdbcTemplate;

    public UserDAO(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // ── Row Mapper ──────────────────────────────────────────────

    private final RowMapper<User> rowMapper = (rs, rowNum) -> User.builder()
            .id(rs.getLong("id"))
            .googleId(rs.getString("google_id"))
            .email(rs.getString("email"))
            .password(rs.getString("password"))
            .avatar(rs.getString("avatar"))
            .displayName(rs.getString("display_name"))
            .team(rs.getString("team"))
            .role(rs.getString("role"))
            .lastLoginIp(rs.getString("last_login_ip"))
            .status(rs.getString("status"))
            .createdAt(rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toLocalDateTime() : null)
            .updatedAt(rs.getTimestamp("updated_at") != null ? rs.getTimestamp("updated_at").toLocalDateTime() : null)
            .build();

    // ── CRUD Operations ─────────────────────────────────────────

    /**
     * Find a user by their internal ID.
     */
    public Optional<User> findById(Long id) {
        String sql = "SELECT * FROM Users WHERE id = ?";
        List<User> results = jdbcTemplate.query(sql, rowMapper, id);
        return results.stream().findFirst();
    }

    /**
     * Find a user by their Google OAuth ID.
     */
    public Optional<User> findByGoogleId(String googleId) {
        String sql = "SELECT * FROM Users WHERE google_id = ?";
        List<User> results = jdbcTemplate.query(sql, rowMapper, googleId);
        return results.stream().findFirst();
    }

    /**
     * Find a user by their email address.
     */
    public Optional<User> findByEmail(String email) {
        String sql = "SELECT * FROM Users WHERE email = ?";
        List<User> results = jdbcTemplate.query(sql, rowMapper, email);
        return results.stream().findFirst();
    }

    /**
     * Insert a new user and return the generated ID.
     */
    public User save(User user) {
        String sql = """
            INSERT INTO Users (google_id, email, password, avatar, display_name, team, role, last_login_ip, status, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, GETDATE(), GETDATE())
            """;

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, user.getGoogleId());
            ps.setString(2, user.getEmail());
            ps.setString(3, user.getPassword());
            ps.setString(4, user.getAvatar());
            ps.setString(5, user.getDisplayName());
            ps.setString(6, user.getTeam());
            ps.setString(7, user.getRole() != null ? user.getRole() : "USER");
            ps.setString(8, user.getLastLoginIp());
            ps.setString(9, user.getStatus() != null ? user.getStatus() : "OFFLINE");
            return ps;
        }, keyHolder);

        Number generatedId = keyHolder.getKey();
        if (generatedId != null) {
            user.setId(generatedId.longValue());
        }
        return user;
    }

    /**
     * Update an existing user's profile information.
     */
    public void update(User user) {
        String sql = """
            UPDATE Users
            SET email = ?, password = ?, avatar = ?, display_name = ?, team = ?, role = ?, status = ?, updated_at = GETDATE()
            WHERE id = ?
            """;
        jdbcTemplate.update(sql,
                user.getEmail(),
                user.getPassword(),
                user.getAvatar(),
                user.getDisplayName(),
                user.getTeam(),
                user.getRole(),
                user.getStatus(),
                user.getId());
    }

    /**
     * Update the user's last login IP address.
     */
    public void updateLastLoginIp(Long userId, String ipAddress) {
        String sql = "UPDATE Users SET last_login_ip = ?, updated_at = GETDATE() WHERE id = ?";
        jdbcTemplate.update(sql, ipAddress, userId);
    }

    /**
     * Update user online status.
     */
    public void updateStatus(Long userId, String status) {
        String sql = "UPDATE Users SET status = ?, updated_at = GETDATE() WHERE id = ?";
        jdbcTemplate.update(sql, status, userId);
    }

    /**
     * Search users by display name (partial match).
     */
    public List<User> searchByName(String query) {
        String sql = "SELECT * FROM Users WHERE display_name LIKE ? ORDER BY display_name";
        return jdbcTemplate.query(sql, rowMapper, "%" + query + "%");
    }

    /**
     * Get all users (for admin panel / user list).
     */
    public List<User> findAll() {
        String sql = "SELECT * FROM Users ORDER BY display_name";
        return jdbcTemplate.query(sql, rowMapper);
    }

    /**
     * Find multiple users by their IDs.
     */
    public List<User> findByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        String placeholders = String.join(",", ids.stream().map(id -> "?").toList());
        String sql = "SELECT * FROM Users WHERE id IN (" + placeholders + ")";
        return jdbcTemplate.query(sql, rowMapper, ids.toArray());
    }
}
