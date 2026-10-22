package ir.linuxian.second.entities.product;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "reviews")
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Getter
    private Long id;

    @Getter @Setter
    @ManyToOne
    @JoinColumn(name = "product_id", nullable = false)
    @JsonIgnore
    private Product product;

    @Getter @Setter
    @Column(nullable = false)
    private String username;

    @Getter @Setter
    @Column(nullable = false)
    private Integer rating;

    @Getter @Setter
    @Column(columnDefinition = "TEXT")
    private String text;

    @Getter
    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Review() {
    }
}
