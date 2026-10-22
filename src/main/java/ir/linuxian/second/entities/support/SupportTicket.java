package ir.linuxian.second.entities.support;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "support_tickets")
public class SupportTicket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Getter
    private Long id;

    @Getter @Setter
    @Column(nullable = false)
    private String subject;

    @Getter @Setter
    @Column(nullable = false)
    private String username;

    @Getter @Setter
    @Column(nullable = false)
    private String status = "OPEN";

    @Getter
    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Getter @Setter
    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    public SupportTicket() {
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = Instant.now();
    }
}
