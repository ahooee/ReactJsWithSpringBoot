package ir.linuxian.second.entities.notification;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Getter
    private Long id;

    @Getter @Setter
    @Column(nullable = false)
    private String username;

    @Getter @Setter
    @Column(nullable = false)
    private String type = "GENERAL";

    @Getter @Setter
    @Column(nullable = false)
    private String subject = "";

    @Getter @Setter
    @Column(columnDefinition = "TEXT")
    private String preview;

    @Getter @Setter
    private String link;

    @Getter @Setter
    @Column(name = "is_read", nullable = false)
    private boolean read = false;

    @Getter
    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Notification() {
    }
}
