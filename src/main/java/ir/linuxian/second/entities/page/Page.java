package ir.linuxian.second.entities.page;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "pages")
public class Page {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Getter
    private Long id;

    @Getter @Setter
    @Column(nullable = false)
    private String title;

    @Getter @Setter
    @Column(nullable = false, unique = true)
    private String slug;

    @Getter @Setter
    @Column(columnDefinition = "TEXT")
    private String content;

    @Getter @Setter
    @Column(nullable = false)
    private String status = "PUBLISHED";

    @Getter @Setter
    @Column(name = "theme", length = 64)
    private String theme = "default";

    @Getter
    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Getter @Setter
    private Instant updatedAt = Instant.now();

    public Page() {
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = Instant.now();
    }
}
