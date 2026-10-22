package ir.linuxian.second.web;

import ir.linuxian.second.service.NotificationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
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

    @GetMapping
    public ResponseEntity<?> list(@RequestParam(defaultValue = "50") int limit,
                                  Authentication authentication) {
        String username = usernameOf(authentication);
        if (username == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login required"));
        }
        return ResponseEntity.ok(notificationService.list(username, limit));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<?> unreadCount(Authentication authentication) {
        String username = usernameOf(authentication);
        if (username == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login required"));
        }
        return ResponseEntity.ok(Map.of("unread", notificationService.unreadCount(username)));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<?> markRead(@PathVariable Long id, Authentication authentication) {
        String username = usernameOf(authentication);
        if (username == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login required"));
        }
        try {
            return ResponseEntity.ok(notificationService.markRead(id, username));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/read-all")
    public ResponseEntity<?> markAllRead(Authentication authentication) {
        String username = usernameOf(authentication);
        if (username == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login required"));
        }
        notificationService.markAllRead(username);
        return ResponseEntity.ok(Map.of("unread", 0));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id, Authentication authentication) {
        String username = usernameOf(authentication);
        if (username == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login required"));
        }
        try {
            notificationService.delete(id, username);
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        }
    }

    @DeleteMapping
    public ResponseEntity<?> clear(Authentication authentication) {
        String username = usernameOf(authentication);
        if (username == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login required"));
        }
        notificationService.clear(username);
        return ResponseEntity.noContent().build();
    }
}
