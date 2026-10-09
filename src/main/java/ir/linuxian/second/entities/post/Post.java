package ir.linuxian.second.entities.post;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "posts")
public class Post {

    @Getter
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Getter
    @Setter
    @Column(nullable = false)
    private String title;

    @Getter
    @Setter
    @Column(nullable = false, unique = true)
    private String slug;

    @Getter
    @Setter
    @Column(columnDefinition = "TEXT")
    private String excerpt;

    @Getter
    @Setter
    @Column(columnDefinition = "TEXT")
    private String content;

    @Getter
    @Setter
    private String coverImage;

    @Getter
    @Setter
    private String author;

    @Getter
    @Setter
    @Column(nullable = false)
    private String status = "published";

    @Getter
    @Setter
    private LocalDateTime createdAt;

    @Getter
    @Setter
    private LocalDateTime updatedAt;

    public Post() {
    }

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        updatedAt = LocalDateTime.now();
        if (status == null || status.isBlank()) {
            status = "published";
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
