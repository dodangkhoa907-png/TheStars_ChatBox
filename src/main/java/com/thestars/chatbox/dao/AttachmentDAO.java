package com.thestars.chatbox.dao;

import com.thestars.chatbox.model.Attachment;
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
 * Data Access Object for the Attachments table.
 */
@Repository
public class AttachmentDAO {

    private final JdbcTemplate jdbcTemplate;

    public AttachmentDAO(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<Attachment> rowMapper = (rs, rowNum) -> Attachment.builder()
            .id(rs.getLong("id"))
            .messageId(rs.getLong("message_id"))
            .fileUrl(rs.getString("file_url"))
            .fileName(rs.getString("file_name"))
            .fileType(rs.getString("file_type"))
            .fileSize(rs.getLong("file_size"))
            .createdAt(rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toLocalDateTime() : null)
            .build();

    /**
     * Save a new attachment.
     */
    public Attachment save(Attachment attachment) {
        String sql = """
            INSERT INTO Attachments (message_id, file_url, file_name, file_type, file_size, created_at)
            VALUES (?, ?, ?, ?, ?, GETDATE())
            """;

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, attachment.getMessageId());
            ps.setString(2, attachment.getFileUrl());
            ps.setString(3, attachment.getFileName());
            ps.setString(4, attachment.getFileType());
            ps.setLong(5, attachment.getFileSize());
            return ps;
        }, keyHolder);

        Number generatedId = keyHolder.getKey();
        if (generatedId != null) {
            attachment.setId(generatedId.longValue());
        }
        return attachment;
    }

    /**
     * Find attachments by message ID.
     */
    public List<Attachment> findByMessageId(Long messageId) {
        String sql = "SELECT * FROM Attachments WHERE message_id = ? ORDER BY created_at";
        return jdbcTemplate.query(sql, rowMapper, messageId);
    }

    /**
     * Find all image attachments in a conversation (for right sidebar gallery).
     */
    public List<Attachment> findImagesByConversationId(Long conversationId) {
        String sql = """
            SELECT a.* FROM Attachments a
            INNER JOIN Messages m ON a.message_id = m.id
            WHERE m.conversation_id = ? AND a.file_type LIKE 'image/%'
            ORDER BY a.created_at DESC
            """;
        return jdbcTemplate.query(sql, rowMapper, conversationId);
    }

    /**
     * Find all non-image file attachments in a conversation (for right sidebar files list).
     */
    public List<Attachment> findFilesByConversationId(Long conversationId) {
        String sql = """
            SELECT a.* FROM Attachments a
            INNER JOIN Messages m ON a.message_id = m.id
            WHERE m.conversation_id = ? AND a.file_type NOT LIKE 'image/%'
            ORDER BY a.created_at DESC
            """;
        return jdbcTemplate.query(sql, rowMapper, conversationId);
    }

    /**
     * Find an attachment by ID.
     */
    public Optional<Attachment> findById(Long id) {
        String sql = "SELECT * FROM Attachments WHERE id = ?";
        List<Attachment> results = jdbcTemplate.query(sql, rowMapper, id);
        return results.stream().findFirst();
    }
}
