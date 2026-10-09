package ir.linuxian.second.entities.media;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "media")
public class Media {

    @Getter
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Getter
    @Setter
    @Column(nullable = false)
    private String filename;

    @Getter
    @Setter
    @Column(nullable = false)
    private String url;

    @Getter
    @Setter
    private String contentType;

    @Getter
    @Setter
    private Long size;

    @Getter
    @Setter
    private String alt;

    @Getter
    @Setter
    private LocalDateTime createdAt;

    public Media() {
    }

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
