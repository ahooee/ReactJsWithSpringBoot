package ir.linuxian.second.entities.support;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "support_ticket_messages")
public class TicketMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Getter
    private Long id;

    @Getter @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id", nullable = false)
    @JsonIgnore
    private SupportTicket ticket;

    @Getter @Setter
    @Column(nullable = false)
    private String senderUsername;

    @Getter @Setter
    @Column(nullable = false)
    private boolean admin = false;

    @Getter @Setter
    @Convert(converter = ir.linuxian.second.service.AesGcmConverter.class)
    @Column(nullable = false, columnDefinition = "TEXT")
    private String text;

    @Getter
    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public TicketMessage() {
    }
}
