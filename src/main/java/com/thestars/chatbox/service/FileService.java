package com.thestars.chatbox.service;

import com.thestars.chatbox.dao.AttachmentDAO;
import com.thestars.chatbox.model.Attachment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Handles file upload, storage, and retrieval.
 */
@Service
public class FileService {

    private static final Logger log = LoggerFactory.getLogger(FileService.class);

    private final AttachmentDAO attachmentDAO;
    private final Path uploadPath;

    public FileService(AttachmentDAO attachmentDAO,
                       @Value("${app.upload.dir:uploads}") String uploadDir) {
        this.attachmentDAO = attachmentDAO;
        this.uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();

        // Create upload directory if it doesn't exist
        try {
            Files.createDirectories(this.uploadPath);
        } catch (IOException e) {
            log.error("Could not create upload directory: {}", uploadPath, e);
        }
    }

    /**
     * Upload a file and create an Attachment record linked to a message.
     */
    public Attachment uploadFile(MultipartFile file, Long messageId) throws IOException {
        // Generate unique filename to prevent collisions
        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        String storedFilename = UUID.randomUUID().toString() + extension;

        // Save file to disk
        Path targetPath = uploadPath.resolve(storedFilename);
        Files.copy(file.getInputStream(), targetPath);

        log.info("File uploaded: {} → {} ({})", originalFilename, storedFilename, file.getSize());

        // Create attachment record
        Attachment attachment = Attachment.builder()
                .messageId(messageId)
                .fileUrl("/uploads/" + storedFilename)
                .fileName(originalFilename != null ? originalFilename : storedFilename)
                .fileType(file.getContentType() != null ? file.getContentType() : "application/octet-stream")
                .fileSize(file.getSize())
                .build();

        return attachmentDAO.save(attachment);
    }

    /**
     * Store a profile avatar image and return its public URL. Unlike
     * {@link #uploadFile}, this isn't linked to a message/Attachment row —
     * a profile picture is just a file the User row points at directly.
     */
    public String storeAvatar(MultipartFile file) throws IOException {
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("Avatar must be an image file");
        }

        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        String storedFilename = UUID.randomUUID().toString() + extension;

        Path targetPath = uploadPath.resolve(storedFilename);
        Files.copy(file.getInputStream(), targetPath);

        log.info("Avatar uploaded: {} -> {} ({} bytes)", originalFilename, storedFilename, file.getSize());
        return "/uploads/" + storedFilename;
    }

    /**
     * Get the filesystem path for a stored file.
     */
    public Optional<Path> getFilePath(String filename) {
        Path filePath = uploadPath.resolve(filename).normalize();
        if (Files.exists(filePath)) {
            return Optional.of(filePath);
        }
        return Optional.empty();
    }

    /**
     * Get all image attachments for a conversation (right sidebar gallery).
     */
    public List<Attachment> getConversationImages(Long conversationId) {
        return attachmentDAO.findImagesByConversationId(conversationId);
    }

    /**
     * Get all file (non-image) attachments for a conversation (right sidebar files).
     */
    public List<Attachment> getConversationFiles(Long conversationId) {
        return attachmentDAO.findFilesByConversationId(conversationId);
    }

    /**
     * Get attachment by ID.
     */
    public Optional<Attachment> findById(Long id) {
        return attachmentDAO.findById(id);
    }
}
