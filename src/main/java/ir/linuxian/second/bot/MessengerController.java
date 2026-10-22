package ir.linuxian.second.bot;

import ir.linuxian.telegram.TelegramException;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Admin-only Messenger API (Telegram-web-like dashboard section). All routes
 * require ROLE_ADMIN (see SecurityConfig).
 */
@RestController
@RequestMapping("/api/admin/messenger")
public class MessengerController {

    private final MessengerService service;

    public MessengerController(MessengerService service) {
        this.service = service;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        return service.status();
    }

    @GetMapping("/chats")
    public List<Map<String, Object>> chats(
            @RequestParam(defaultValue = "50") int limit) throws TelegramException {
        return service.listChats(limit);
    }

    @GetMapping("/chats/{chatId}")
    public Map<String, Object> chat(@PathVariable long chatId) throws TelegramException {
        return service.getChat(chatId);
    }

    @GetMapping("/resolve")
    public Map<String, Object> resolve(@RequestParam String target) throws TelegramException {
        return service.resolveTarget(target);
    }

    @GetMapping("/chats/{chatId}/messages")
    public Map<String, Object> messages(
            @PathVariable long chatId,
            @RequestParam(defaultValue = "0") long fromMessageId,
            @RequestParam(defaultValue = "50") int limit) throws TelegramException {
        return service.getMessages(chatId, fromMessageId, limit);
    }

    public record ReadRequest(List<Long> messageIds) {
    }

    @PostMapping("/chats/{chatId}/read")
    public Map<String, Object> markRead(
            @PathVariable long chatId,
            @RequestBody(required = false) ReadRequest body) throws TelegramException {
        return service.markRead(chatId, body == null ? List.of() : body.messageIds());
    }

    public record TextRequest(String text, Long replyToMessageId) {
    }

    @PostMapping("/chats/{chatId}/messages")
    public Map<String, Object> sendText(
            @PathVariable long chatId,
            @RequestBody TextRequest body) throws TelegramException {
        String text = body == null ? null : body.text();
        long replyTo = body != null && body.replyToMessageId() != null ? body.replyToMessageId() : 0;
        return service.sendText(chatId, text, replyTo);
    }

    /**
     * Sends a file to a chat. Multipart form: file (required), kind
     * (auto|photo|video|audio|voice|document, default auto), caption
     * (optional), replyToMessageId (optional).
     */
    @PostMapping("/chats/{chatId}/files")
    public Map<String, Object> sendFile(
            @PathVariable long chatId,
            @RequestParam(required = false) MultipartFile file,
            @RequestParam(defaultValue = "auto") String kind,
            @RequestParam(required = false) String caption,
            @RequestParam(required = false, defaultValue = "0") long replyToMessageId)
            throws TelegramException, IOException {
        return service.sendFile(chatId, kind, caption, file, replyToMessageId);
    }

    /**
     * Downloads a Telegram file (message attachment or chat photo) and
     * streams it back so the dashboard can display it inline.
     */
    @GetMapping("/files/{fileId}")
    public ResponseEntity<Resource> file(
            @PathVariable int fileId,
            @RequestParam(required = false) String name) throws TelegramException, IOException {
        Path path = service.downloadFilePath(fileId);
        String contentType = Files.probeContentType(path);
        MediaType mediaType;
        try {
            mediaType = contentType == null
                    ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(contentType);
        } catch (Exception e) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }
        String filename = name == null || name.isBlank() ? "telegram-file-" + fileId : name;
        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(Files.size(path))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .body(new InputStreamResource(Files.newInputStream(path)));
    }

    // ---------- errors ----------

    @ExceptionHandler(TelegramException.class)
    public ResponseEntity<Map<String, String>> telegramError(TelegramException e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of("message", "Telegram error: " + e.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> notReady(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("message", e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
    }
}
