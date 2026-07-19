package com.thestars.chatbox.controller;

import com.thestars.chatbox.model.Attachment;
import com.thestars.chatbox.model.Message;
import com.thestars.chatbox.model.User;
import com.thestars.chatbox.service.FileService;
import com.thestars.chatbox.service.MessageService;
import com.thestars.chatbox.service.UserService;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import java.security.Principal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Handles file upload and download.
 */
@RestController
@RequestMapping("/api")
public class FileController {

    private final FileService fileService;
    private final MessageService messageService;
    private final UserService userService;

    public FileController(FileService fileService, MessageService messageService,
                          UserService userService) {
        this.fileService = fileService;
        this.messageService = messageService;
        this.userService = userService;
    }

    /**
     * POST /api/upload — Upload a file attached to a conversation.
     * Creates a FILE/IMAGE message and links the attachment.
     */
    @PostMapping("/upload")
    public ResponseEntity<?> uploadFile(@RequestParam("file") MultipartFile file,
                                        @RequestParam("conversationId") Long conversationId,
                                        Principal principal) {
        Optional<User> user = userService.findByEmail(principal.getName());
        if (user.isEmpty()) return ResponseEntity.status(401).build();

        try {
            // Determine message type based on file MIME type
            String contentType = file.getContentType();
            String messageType = (contentType != null && contentType.startsWith("image/")) ? "IMAGE" : "FILE";

            // Create the message first
            Message message = messageService.sendMessage(
                    conversationId,
                    user.get().getId(),
                    file.getOriginalFilename(),
                    messageType
            );

            // Upload file and create attachment
            Attachment attachment = fileService.uploadFile(file, message.getId());

            return ResponseEntity.ok(Map.of(
                    "message", message,
                    "attachment", attachment
            ));
        } catch (IOException e) {
            return ResponseEntity.status(500)
                    .body(Map.of("error", "File upload failed: " + e.getMessage()));
        }
    }

    /**
     * GET /api/files/{id} — Download a file by attachment ID.
     */
    @GetMapping("/files/{id}")
    public ResponseEntity<Resource> downloadFile(@PathVariable Long id) {
        Optional<Attachment> attachment = fileService.findById(id);
        if (attachment.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        String fileUrl = attachment.get().getFileUrl();
        // Extract filename from URL: /uploads/uuid.ext → uuid.ext
        String filename = fileUrl.substring(fileUrl.lastIndexOf("/") + 1);
        Optional<Path> filePath = fileService.getFilePath(filename);

        if (filePath.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        try {
            Resource resource = new UrlResource(filePath.get().toUri());
            // Build via ContentDisposition rather than string-concatenating the
            // (client-supplied) original filename into the header value — a quote
            // or control character in it would otherwise corrupt/inject into the
            // Content-Disposition header.
            ContentDisposition disposition = ContentDisposition.attachment()
                    .filename(attachment.get().getFileName())
                    .build();
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(attachment.get().getFileType()))
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * GET /api/conversations/{id}/attachments — List attachments for right sidebar.
     * @param type "image" or "file"
     */
    @GetMapping("/conversations/{id}/attachments")
    public ResponseEntity<?> getAttachments(@PathVariable Long id,
                                            @RequestParam(defaultValue = "all") String type) {
        if ("image".equals(type)) {
            return ResponseEntity.ok(fileService.getConversationImages(id));
        } else if ("file".equals(type)) {
            return ResponseEntity.ok(fileService.getConversationFiles(id));
        } else {
            // Return both
            List<Attachment> images = fileService.getConversationImages(id);
            List<Attachment> files = fileService.getConversationFiles(id);
            return ResponseEntity.ok(Map.of("images", images, "files", files));
        }
    }
}
