package ir.linuxian.second.entities.product;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Getter
    private Long id;

    @Getter @Setter
    @Column(nullable = false)
    private String name;

    @Getter @Setter
    @Column(nullable = false, unique = true)
    private String slug;

    @Getter @Setter
    @Column(columnDefinition = "TEXT")
    private String description;

    @Getter @Setter
    @Column(nullable = false)
    private BigDecimal price = BigDecimal.ZERO;

    @Getter @Setter
    private String imageUrl;

    @Getter @Setter
    @Column(nullable = false)
    private Integer stock = 0;

    @Getter @Setter
    @Column(nullable = false)
    private Boolean active = true;

    @Getter @Setter
    @Column(nullable = false)
    @ColumnDefault("1")
    private Integer maxPerOrder = 1;

    @Getter @Setter
    @ManyToOne
    @JoinColumn(name = "category_id")
    private Category category;

    // Bookstore fields (all optional)
    @Getter @Setter
    private String author;

    @Getter @Setter
    private String translator;

    @Getter @Setter
    private String publisher;

    @Getter @Setter
    private String isbn;

    @Getter @Setter
    private String language;

    @Getter @Setter
    private String coverType;

    @Getter @Setter
    private String bookSize;

    @Getter @Setter
    private Integer pageCount;

    @Getter @Setter
    private Integer edition;

    @Getter @Setter
    private Integer weightGrams;

    @Getter @Setter
    private String publishYear;

    @Getter @Setter
    private Integer discountPercent;

    /** PDF or PAPER. PDF books need no shipping address at checkout. */
    @Getter @Setter
    private String bookType = "PDF";

    @Getter @Setter
    @Column(nullable = false)
    @ColumnDefault("0")
    private Long viewCount = 0L;

    @Getter @Setter
    @Column(nullable = false)
    @ColumnDefault("0")
    private Long likeCount = 0L;

    @Getter @Setter
    @Column(nullable = false)
    @ColumnDefault("0")
    private Long wantCount = 0L;

    @Getter @Setter
    @Column(nullable = false)
    @ColumnDefault("0")
    private Long recommendUp = 0L;

    @Getter @Setter
    @Column(nullable = false)
    @ColumnDefault("0")
    private Long recommendDown = 0L;

    @Getter
    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Getter @Setter
    private Instant updatedAt = Instant.now();

    public Product() {
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = Instant.now();
    }
}
