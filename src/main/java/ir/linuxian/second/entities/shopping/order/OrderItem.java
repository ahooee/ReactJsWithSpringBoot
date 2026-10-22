package ir.linuxian.second.entities.shopping.order;

import ir.linuxian.second.entities.product.Product;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Frozen line of an Order. product may become null if the catalog row is
 * deleted; productName/unitPrice snapshots always remain for history.
 */
@Entity
@Table(name = "order_items")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(nullable = false, updatable = false)
    private String productName;

    @Column(nullable = false, updatable = false, precision = 38, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false, updatable = false)
    private int quantity;

    public OrderItem(Order order, Product product, String productName,
                     BigDecimal unitPrice, int quantity) {
        this.order = order;
        this.product = product;
        this.productName = productName;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
    }

    public static OrderItem snapshot(Order order, Product product, int quantity) {
        return new OrderItem(order, product,
                product.getName(),
                product.getPrice() == null ? BigDecimal.ZERO : product.getPrice(),
                quantity);
    }

    /** Snapshot using the effective (discounted) price. */
    public static OrderItem snapshotWithPrice(Order order, Product product,
                                              BigDecimal effectiveUnitPrice, int quantity) {
        return new OrderItem(order, product,
                product.getName(),
                effectiveUnitPrice == null ? BigDecimal.ZERO : effectiveUnitPrice,
                quantity);
    }

    public BigDecimal lineTotal() {
        if (unitPrice == null) return BigDecimal.ZERO;
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
