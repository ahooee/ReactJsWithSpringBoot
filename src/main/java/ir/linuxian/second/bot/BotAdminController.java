package ir.linuxian.second.bot;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Admin-only Bot Manager API. All routes require ROLE_ADMIN
 * (see SecurityConfig).
 */
@RestController
@RequestMapping("/api/admin/bot")
public class BotAdminController {

    private final BotManagementService service;

    public BotAdminController(BotManagementService service) {
        this.service = service;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        return service.status();
    }

    @GetMapping("/settings")
    public Map<String, Object> settings() {
        return service.settings();
    }

    @PutMapping("/settings")
    public Map<String, Object> updateSettings(@RequestBody Map<String, String> values) {
        return service.updateSettings(values == null ? Map.of() : values);
    }

    @PostMapping("/restart")
    public Map<String, Object> restart() {
        return service.restart();
    }

    // ---------- telegram account login / logout (Admin → Settings) ----------

    @GetMapping("/login/status")
    public Map<String, Object> loginStatus() {
        return service.loginStatus();
    }

    public record LoginPhoneRequest(String phone) {
    }

    @PostMapping("/login/start")
    public ResponseEntity<?> loginStart(@RequestBody LoginPhoneRequest body) {
        try {
            return ResponseEntity.ok(service.loginStart(body == null ? null : body.phone()));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return bad(e.getMessage());
        } catch (Exception e) {
            return bad("Login start failed: " + e.getMessage());
        }
    }

    public record LoginCodeRequest(String code) {
    }

    @PostMapping("/login/code")
    public ResponseEntity<?> loginCode(@RequestBody LoginCodeRequest body) {
        try {
            return ResponseEntity.ok(service.loginCode(body == null ? null : body.code()));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return bad(e.getMessage());
        } catch (Exception e) {
            return bad("Code check failed: " + e.getMessage());
        }
    }

    public record LoginPasswordRequest(String password) {
    }

    @PostMapping("/login/password")
    public ResponseEntity<?> loginPassword(@RequestBody LoginPasswordRequest body) {
        try {
            return ResponseEntity.ok(service.loginPassword(body == null ? null : body.password()));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return bad(e.getMessage());
        } catch (Exception e) {
            return bad("Password check failed: " + e.getMessage());
        }
    }

    @PostMapping("/login/cancel")
    public Map<String, Object> loginCancel() {
        return service.loginCancel();
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout() {
        try {
            return ResponseEntity.ok(service.logout());
        } catch (IllegalStateException e) {
            return bad(e.getMessage());
        } catch (Exception e) {
            return bad("Logout failed: " + e.getMessage());
        }
    }

    @GetMapping("/allowlist")
    public java.util.List<Map<String, Object>> allowlist() {
        return service.allowedChats();
    }

    public record AllowRequest(String target) {
    }

    @PostMapping("/allowlist")
    public ResponseEntity<?> addAllowed(@RequestBody AllowRequest body) {
        try {
            if (body == null || body.target() == null || body.target().isBlank()) {
                return bad("Target is required (username, user id or phone).");
            }
            return ResponseEntity.ok(service.addAllowedChat(body.target()));
        } catch (Exception e) {
            return bad("Could not add '" + (body == null ? "" : body.target()) + "': " + e.getMessage());
        }
    }

    @DeleteMapping("/allowlist/{chatId}")
    public ResponseEntity<?> removeAllowed(@PathVariable long chatId) {
        service.removeAllowedChat(chatId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/allowlist")
    public ResponseEntity<?> clearAllowed() {
        service.clearAllowedChats();
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/ai-models")
    public Map<String, Object> aiModels(@RequestParam(required = false) String apiKey,
                                        @RequestParam(required = false) String baseUrl) {
        return service.aiModels(apiKey, baseUrl);
    }

    /**
     * Sends any message kind. Multipart form: target, kind
     * (text|photo|document|video|audio|voice|location), text, caption,
     * latitude, longitude, file (for media kinds). Returns a job id; poll
     * {@code GET /send/{jobId}} for the progress bar.
     */
    @PostMapping("/send")
    public ResponseEntity<?> send(
            @RequestParam String target,
            @RequestParam(defaultValue = "text") String kind,
            @RequestParam(required = false) String text,
            @RequestParam(required = false) String caption,
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude,
            @RequestParam(required = false) MultipartFile file) {
        try {
            String filePath = null;
            if (file != null && !file.isEmpty()) {
                filePath = service.storeUpload(file);
            }
            BotSendJob job = service.send(new BotManagementService.SendRequest(
                    target, kind, text, caption, filePath, latitude, longitude));
            return ResponseEntity.ok(Map.of("jobId", job.getJobId()));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return bad(e.getMessage());
        } catch (Exception e) {
            return bad("Send failed: " + e.getMessage());
        }
    }

    @GetMapping("/send/{jobId}")
    public ResponseEntity<?> job(@PathVariable String jobId) {
        try {
            BotSendJob job = service.getJob(jobId);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("jobId", job.getJobId());
            m.put("status", job.getStatus().name());
            m.put("kind", job.getKind());
            m.put("target", job.getTarget());
            m.put("chatTitle", job.getChatTitle());
            m.put("percent", job.getPercent());
            m.put("doneBytes", job.getDoneBytes());
            m.put("totalBytes", job.getTotalBytes());
            m.put("message", job.getMessage());
            m.put("error", job.getError());
            m.put("messageId", job.getMessageId());
            return ResponseEntity.ok(m);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        }
    }

    private static ResponseEntity<Map<String, String>> bad(String message) {
        return ResponseEntity.badRequest().body(Map.of("message", message));
    }
}
