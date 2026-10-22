package ir.linuxian.second.entities.media;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "media_assets")
public class MediaAsset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Getter
    private Long id;

    @Getter @Setter
    @Column(nullable = false)
    private String filename;

    @Getter @Setter
    private String originalName;

    @Getter @Setter
    private String contentType;

    @Getter @Setter
    private Long size;

    @Getter @Setter
    @Column(nullable = false)
    private String url;

    @Getter
    @Column(nullable = false, updatable = false)
    private Instant uploadedAt = Instant.now();

    public MediaAsset() {
    }
}
