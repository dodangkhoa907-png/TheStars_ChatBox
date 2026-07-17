package com.thestars.chatbox.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Represents a file/image attachment linked to a message.
 * Maps to the [Attachments] table in SQL Server.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Attachment {

    private Long id;
    private Long messageId;

    /** URL path to the stored file */
    private String fileUrl;

    /** Original filename */
    private String fileName;

    /** MIME type: image/png, application/pdf, etc. */
    private String fileType;

    /** File size in bytes */
    @Builder.Default
    private long fileSize = 0;

    private LocalDateTime createdAt;

    /**
     * Helper: Check if this attachment is an image.
     */
    public boolean isImage() {
        return fileType != null && fileType.startsWith("image/");
    }
}
