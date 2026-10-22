package ir.linuxian.second.entities.payment;

import ir.linuxian.second.entities.shopping.Auditable;
import ir.linuxian.second.entities.shopping.order.Order;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment extends Auditable {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(nullable = false, unique = true, updatable = false)
    private String authority;

    @Column(nullable = false, updatable = false, precision = 38, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status = PaymentStatus.PENDING;

    private Long refId;

    @Getter @Setter
    @Convert(converter = ir.linuxian.second.service.AesGcmConverter.class)
    @Column(columnDefinition = "TEXT")
    private String cardPan;

    public static Payment start(Order order, String authority) {
        Payment p = new Payment();
        p.order = order;
        p.authority = authority;
        p.amount = order.getTotalAmount();
        return p;
    }

    public void markSuccess(long refId, String cardPan) {
        this.status = PaymentStatus.SUCCESS; this.refId = refId; this.cardPan = cardPan;
    }
    public void markFailed() { this.status = PaymentStatus.FAILED; }
}