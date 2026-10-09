package ir.linuxian.second.entities.product;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "products")
public class Product {

    @Getter
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Getter
    @Setter
    @Column(nullable = false)
    private String name;

    @Getter
    @Setter
    @Column(nullable = false, unique = true)
    private String slug;

    @Getter
    @Setter
    @Column(columnDefinition = "TEXT")
    private String description;

    @Getter
    @Setter
    private BigDecimal price;

    @Getter
    @Setter
    private String imageUrl;

    @Getter
    @Setter
    private String category;

    @Getter
    @Setter
    private Integer stock;

    @Getter
    @Setter
    @Column(nullable = false)
    private boolean featured = false;

    @Getter
    @Setter
    private LocalDateTime createdAt;

    public Product() {
    }

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
