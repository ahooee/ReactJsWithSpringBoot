package ir.linuxian.second.entities.product;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * One counted view of a product by a single viewer.
 * Uniqueness of (product, viewer) is what makes view counts
 * repeat-proof: refreshes never count twice.
 */
@Entity
@Table(name = "product_views",
        uniqueConstraints = @UniqueConstraint(columnNames = {"product_id", "viewer_key"}))
public class ProductView {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Getter
    private Long id;

    @Getter @Setter
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /**
     * Viewer identity: {@code u:{username}} for logged-in users,
     * {@code t:{viewerToken}} for guests. A guest row is adopted
     * (re-keyed to the user) on first authenticated view, so logging
     * in never counts the same view twice.
     */
    @Getter @Setter
    @Column(name = "viewer_key", nullable = false)
    private String viewerKey;

    @Getter
    @Column(nullable = false, updatable = false)
    private Instant viewedAt = Instant.now();

    public ProductView() {
    }

    public ProductView(Product product, String viewerKey) {
        this.product = product;
        this.viewerKey = viewerKey;
    }
}
