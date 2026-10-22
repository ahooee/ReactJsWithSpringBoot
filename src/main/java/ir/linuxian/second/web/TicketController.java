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
@RequestMapping("/api/tickets")
public class TicketController {

    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
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
    public ResponseEntity<?> list(Authentication authentication) {
        String username = usernameOf(authentication);
        if (username == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login required"));
        }
        return ResponseEntity.ok(ticketService.listForUser(username));
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Map<String, Object> body, Authentication authentication) {
        String username = usernameOf(authentication);
        if (username == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login required"));
        }
        try {
            String subject = body.get("subject") == null ? null : String.valueOf(body.get("subject"));
            String text = body.get("message") != null ? String.valueOf(body.get("message"))
                    : body.get("text") == null ? null : String.valueOf(body.get("text"));
            SupportTicket ticket = ticketService.create(username, subject, text);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "ticket", ticket,
                    "messages", ticketService.messages(ticket.getId())));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> detail(@PathVariable Long id, Authentication authentication) {
        String username = usernameOf(authentication);
        if (username == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login required"));
        }
        try {
            SupportTicket ticket = ticketService.getForUser(id, username);
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
        String username = usernameOf(authentication);
        if (username == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login required"));
        }
        try {
            Object raw = body.get("message") != null ? body.get("message") : body.get("text");
            String text = raw == null ? null : String.valueOf(raw);
            TicketMessage saved = ticketService.addUserMessage(id, username, text);
            return ResponseEntity.status(HttpStatus.CREATED).body(saved);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
}
