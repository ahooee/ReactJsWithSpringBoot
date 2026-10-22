package ir.linuxian.second.entities.shopping.order;

import ir.linuxian.second.entities.User;
import ir.linuxian.second.entities.shopping.Auditable;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static jakarta.persistence.CascadeType.ALL;

/**
 * Persistent purchase snapshot. Created from a Basket at checkout.
 * Items are copied (product name + unit price frozen) so later product
 * edits/deletions never change history. Payment is tracked separately
 * in {@code Payment} rows linked to this order.
 */
@Entity
@Table(name = "orders")
public class Order extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Getter
    private Long id;

    /** Human-readable number shown to the customer (ORD-...). */
    @Getter @Setter
    @Column(nullable = false, unique = true)
    private String orderNumber;

    /**
     * Secret token for guest tracking + payment.
     * Logged-in users access via /api/orders/{id}, guests via
     * /api/orders/track/{accessToken}. Also used as {token} in pay URLs.
     */
    @Getter @Setter
    @Column(nullable = false, unique = true, updatable = false)
    private String accessToken;

    @Getter @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Getter @Setter
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status = OrderStatus.PENDING_PAYMENT;

    /** Frozen total at checkout time (IRR). */
    @Getter @Setter
    @Column(nullable = false, precision = 38, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Getter @Setter
    @Convert(converter = ir.linuxian.second.service.AesGcmConverter.class)
    @Column(columnDefinition = "TEXT")
    private String customerName;

    @Getter @Setter
    @Convert(converter = ir.linuxian.second.service.AesGcmConverter.class)
    @Column(columnDefinition = "TEXT")
    private String customerPhone;

    @Getter @Setter
    @Convert(converter = ir.linuxian.second.service.AesGcmConverter.class)
    @Column(columnDefinition = "TEXT")
    private String customerAddress;

    @Getter @Setter
    @Convert(converter = ir.linuxian.second.service.AesGcmConverter.class)
    @Column(columnDefinition = "TEXT")
    private String customerEmail;

    /** Zarinpal ref_id after successful verification (nullable). */
    @Getter @Setter
    private String paidRefId;

    @Getter @Setter
    @Column(nullable = false, updatable = false)
    private Instant orderedAt = Instant.now();

    @Getter @Setter
    @OneToMany(mappedBy = "order", cascade = ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    public Order() {
    }

    public boolean isPayable() {
        return status == OrderStatus.PENDING_PAYMENT
                || status == OrderStatus.PAYMENT_FAILED;
    }

    public void markPaid(String refId) {
        this.status = OrderStatus.PAID;
        this.paidRefId = refId;
    }

    public void markPaymentFailed() {
        this.status = OrderStatus.PAYMENT_FAILED;
    }
}
