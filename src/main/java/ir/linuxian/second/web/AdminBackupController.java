package ir.linuxian.second.web;

import ir.linuxian.second.service.BackupService;
import ir.linuxian.second.service.VerificationService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Database backup &amp; restore (admin only). Every operation burns a
 * fresh 2FA code sent to the admin's own email or Telegram.
 * (Restart trigger: no functional change.)
 */
@RestController
@RequestMapping("/api/admin/backup")
public class AdminBackupController {

    private final BackupService backupService;
    private final VerificationService verificationService;

    public AdminBackupController(BackupService backupService, VerificationService verificationService) {
        this.backupService = backupService;
        this.verificationService = verificationService;
    }

    /** Step 1: send a code. Body: {operation: "backup"|"restore", channel: "email"|"phone"} */
    @PostMapping("/code")
    public ResponseEntity<?> sendCode(Authentication authentication, @RequestBody Map<String, Object> body) {
        try {
            String operation = body == null ? null : String.valueOf(body.get("operation"));
            String channel = body == null ? null : String.valueOf(body.get("channel"));
            if ("phone".equalsIgnoreCase(channel)) {
                channel = "PHONE";
            } else {
                channel = "EMAIL";
            }
            return ResponseEntity.ok(verificationService.requestAdminCode(
                    usernameOf(authentication), operation, channel));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * Step 2 (backup): verify the code and download the dump. Body: {requestId, code}.
     *
     * NOTE: the stream is returned directly (not wrapped in ResponseEntity):
     * a wrapped StreamingResponseBody goes through message converters, which
     * have no converter for it ("No converter for ... with preset
     * Content-Type"). Returned bare, the streaming handler writes it.
     */
    @PostMapping("/download")
    public StreamingResponseBody download(Authentication authentication,
                                          @RequestBody Map<String, Object> body,
                                          jakarta.servlet.http.HttpServletResponse response) {
        String requestId = body == null ? null : String.valueOf(body.get("requestId"));
        String code = body == null ? null : String.valueOf(body.get("code"));
        try {
            verificationService.verifyAdminCode(usernameOf(authentication), "BACKUP", requestId, code);
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
        final Path file;
        try {
            file = backupService.backup();
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    e.getMessage() == null ? "Backup failed" : e.getMessage(), e);
        }
        String filename = file.getFileName().toString();
        final long size;
        try {
            size = Files.size(file);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Backup failed", e);
        }
        response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition
                .attachment()
                .filename(filename)
                .build().toString());
        response.setContentLengthLong(size);
        return out -> {
            try (var in = Files.newInputStream(file)) {
                in.transferTo(out);
            } finally {
                try {
                    Files.deleteIfExists(file);
                } catch (Exception ignored) {
                    // best effort
                }
            }
        };
    }

    /** Step 2 (restore): verify the code and restore the uploaded dump. */
    @PostMapping(value = "/restore", consumes = {"multipart/form-data"})
    public ResponseEntity<?> restore(
            Authentication authentication,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "requestId", required = false) String requestId,
            @RequestParam(value = "code", required = false) String code) {
        Path upload = null;
        try {
            if (file == null || file.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("message", "Upload a backup file (.dump)"));
            }
            upload = Files.createTempFile("db-restore-", ".dump");
            file.transferTo(upload);
            // Validate the archive BEFORE burning the single-use code.
            backupService.validate(upload);
            verificationService.verifyAdminCode(usernameOf(authentication), "RESTORE", requestId, code);
            backupService.restore(upload);
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage() == null ? "Restore failed" : e.getMessage()));
        } finally {
            if (upload != null) {
                try {
                    Files.deleteIfExists(upload);
                } catch (Exception ignored) {
                    // best effort
                }
            }
        }
    }

    private String usernameOf(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        String name = authentication.getName();
        if (name == null || name.equals("anonymousUser")) {
            return null;
        }
        return name;
    }
}
