package ir.linuxian.second.web;

import ir.linuxian.second.service.BookUploadJob;
import ir.linuxian.second.service.BookUploadService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Admin-only API for the "Telegram book upload" page. All routes require
 * ROLE_ADMIN (see SecurityConfig).
 */
@RestController
@RequestMapping("/api/admin/books/upload")
public class AdminBookUploadController {

    private final BookUploadService service;

    public AdminBookUploadController(BookUploadService service) {
        this.service = service;
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        return service.stats();
    }

    @GetMapping("/active")
    public ResponseEntity<?> active() {
        BookUploadJob job = service.getActiveJob();
        if (job == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(service.snapshot(job));
    }

    @PostMapping
    public ResponseEntity<?> start(@RequestBody Map<String, String> body) {
        try {
            String path = body == null ? null : body.get("downloadsPath");
            String chat = body == null ? null : body.get("chatLink");
            BookUploadJob job = service.start(path, chat);
            return ResponseEntity.ok(service.snapshot(job));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return bad(e.getMessage());
        } catch (Exception e) {
            return bad("Could not start upload: " + e.getMessage());
        }
    }

    @GetMapping("/{jobId}")
    public ResponseEntity<?> job(@PathVariable String jobId) {
        try {
            return ResponseEntity.ok(service.snapshot(service.getJob(jobId)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/{jobId}/stop")
    public ResponseEntity<?> stop(@PathVariable String jobId) {
        try {
            return ResponseEntity.ok(service.snapshot(service.stop(jobId)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        }
    }

    private static ResponseEntity<Map<String, String>> bad(String message) {
        return ResponseEntity.badRequest().body(Map.of("message", message == null ? "Bad request." : message));
    }
}
