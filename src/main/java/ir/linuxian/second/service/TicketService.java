package ir.linuxian.second.service;

import ir.linuxian.second.entities.support.SupportTicket;
import ir.linuxian.second.entities.support.TicketMessage;
import ir.linuxian.second.repos.SupportTicketRepo;
import ir.linuxian.second.repos.TicketMessageRepo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
public class TicketService {

    private static final Set<String> STATUSES = Set.of("OPEN", "ANSWERED", "CLOSED");

    private final SupportTicketRepo ticketRepo;
    private final TicketMessageRepo messageRepo;
    private final NotificationService notificationService;

    public TicketService(SupportTicketRepo ticketRepo, TicketMessageRepo messageRepo,
                         NotificationService notificationService) {
        this.ticketRepo = ticketRepo;
        this.messageRepo = messageRepo;
        this.notificationService = notificationService;
    }

    public List<SupportTicket> listForUser(String username) {
        return ticketRepo.findByUsernameOrderByUpdatedAtDesc(username);
    }

    public List<SupportTicket> listAll(String status) {
        if (status != null && !status.isBlank()) {
            String s = status.trim().toUpperCase();
            if (!STATUSES.contains(s)) {
                throw new RuntimeException("Invalid status: " + status);
            }
            return ticketRepo.findByStatusOrderByUpdatedAtDesc(s);
        }
        return ticketRepo.findAllByOrderByUpdatedAtDesc();
    }

    public SupportTicket getOrThrow(Long id) {
        return ticketRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Ticket not found: " + id));
    }

    public SupportTicket getForUser(Long id, String username) {
        SupportTicket ticket = getOrThrow(id);
        if (!ticket.getUsername().equals(username)) {
            throw new RuntimeException("Ticket not found: " + id);
        }
        return ticket;
    }

    public List<TicketMessage> messages(Long ticketId) {
        return messageRepo.findByTicketIdOrderByCreatedAtAsc(ticketId);
    }

    @Transactional
    public SupportTicket create(String username, String subject, String text) {
        if (subject == null || subject.isBlank()) {
            throw new RuntimeException("Subject is required");
        }
        if (text == null || text.isBlank()) {
            throw new RuntimeException("Message text is required");
        }
        SupportTicket ticket = new SupportTicket();
        ticket.setSubject(subject.trim());
        ticket.setUsername(username);
        ticket.setStatus("OPEN");
        ticketRepo.save(ticket);

        TicketMessage first = new TicketMessage();
        first.setTicket(ticket);
        first.setSenderUsername(username);
        first.setAdmin(false);
        first.setText(text.trim());
        messageRepo.save(first);
        return ticket;
    }

    @Transactional
    public TicketMessage addUserMessage(Long ticketId, String username, String text) {
        SupportTicket ticket = getForUser(ticketId, username);
        if (text == null || text.isBlank()) {
            throw new RuntimeException("Message text is required");
        }
        TicketMessage message = new TicketMessage();
        message.setTicket(ticket);
        message.setSenderUsername(username);
        message.setAdmin(false);
        message.setText(text.trim());
        messageRepo.save(message);
        // A user reply re-opens a closed/answered ticket.
        ticket.setStatus("OPEN");
        ticketRepo.save(ticket);
        return message;
    }

    @Transactional
    public TicketMessage addAdminMessage(Long ticketId, String adminUsername, String text) {
        SupportTicket ticket = getOrThrow(ticketId);
        if (text == null || text.isBlank()) {
            throw new RuntimeException("Message text is required");
        }
        TicketMessage message = new TicketMessage();
        message.setTicket(ticket);
        message.setSenderUsername(adminUsername);
        message.setAdmin(true);
        message.setText(text.trim());
        messageRepo.save(message);
        ticket.setStatus("ANSWERED");
        ticketRepo.save(ticket);
        notificationService.notify(ticket.getUsername(), "TICKET_ANSWER",
                ticket.getSubject(), excerpt(text), "/support");
        return message;
    }

    private static String excerpt(String text) {
        if (text == null) return null;
        String t = text.trim();
        return t.length() > 160 ? t.substring(0, 160) + "…" : t;
    }

    @Transactional
    public SupportTicket setStatus(Long ticketId, String status) {
        SupportTicket ticket = getOrThrow(ticketId);
        String s = status == null ? "" : status.trim().toUpperCase();
        if (!STATUSES.contains(s)) {
            throw new RuntimeException("Invalid status. Use OPEN, ANSWERED or CLOSED");
        }
        ticket.setStatus(s);
        SupportTicket saved = ticketRepo.save(ticket);
        if ("CLOSED".equals(s) || "OPEN".equals(s)) {
            notificationService.notify(ticket.getUsername(), "TICKET_STATUS",
                    ticket.getSubject(), s, "/support");
        }
        return saved;
    }

    @Transactional
    public void delete(Long ticketId) {
        if (!ticketRepo.existsById(ticketId)) {
            throw new RuntimeException("Ticket not found: " + ticketId);
        }
        messageRepo.deleteByTicketId(ticketId);
        ticketRepo.deleteById(ticketId);
    }

    public long countOpen() {
        return ticketRepo.countByStatus("OPEN");
    }
}
