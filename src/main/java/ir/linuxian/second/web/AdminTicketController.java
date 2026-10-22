package ir.linuxian.second.web;

import ir.linuxian.second.entities.support.SupportTicket;
import ir.linuxian.second.entities.support.TicketMessage;
import ir.linuxian.second.service.TicketService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/tickets")
public class AdminTicketController {

    private final TicketService ticketService;

    public AdminTicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @GetMapping
    public ResponseEntity<?> list(@RequestParam(required = false) String status) {
        try {
            return ResponseEntity.ok(ticketService.listAll(status));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/open-count")
    public Map<String, Long> openCount() {
        return Map.of("open", ticketService.countOpen());
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> detail(@PathVariable Long id) {
        try {
            SupportTicket ticket = ticketService.getOrThrow(id);
            List<TicketMessage> messages = ticketService.messages(id);
            return ResponseEntity.ok(Map.of("ticket", ticket, "messages", messages));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/{id}/messages")
    public ResponseEntity<?> reply(@PathVariable Long id,
                                   @RequestBody Map<String, Object> body,
                                   Authentication authentication) {
        try {
            Object raw = body.get("message") != null ? body.get("message") : body.get("text");
            String text = raw == null ? null : String.valueOf(raw);
            String admin = authentication == null ? "admin" : authentication.getName();
            TicketMessage saved = ticketService.addAdminMessage(id, admin, text);
            return ResponseEntity.status(HttpStatus.CREATED).body(saved);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<?> setStatus(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        try {
            Object raw = body.get("status");
            String status = raw == null ? null : String.valueOf(raw);
            return ResponseEntity.ok(ticketService.setStatus(id, status));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        try {
            ticketService.delete(id);
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        }
    }
}
