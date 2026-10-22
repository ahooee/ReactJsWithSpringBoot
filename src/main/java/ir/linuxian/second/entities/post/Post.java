package ir.linuxian.second.entities.post;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "posts")
public class Post {

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
    @Column(length = 500)
    private String excerpt;

    @Getter @Setter
    @Column(columnDefinition = "TEXT")
    private String content;

    @Getter @Setter
    private String coverImage;

    @Getter @Setter
    @Column(nullable = false)
    private String status = "PUBLISHED";

    @Getter @Setter
    private String authorUsername;

    @Getter
    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Getter @Setter
    private Instant updatedAt = Instant.now();

    public Post() {
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = Instant.now();
    }
}
