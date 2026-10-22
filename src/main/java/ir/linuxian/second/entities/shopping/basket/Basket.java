package ir.linuxian.second.entities.shopping.basket;

import ir.linuxian.second.entities.User;
import ir.linuxian.second.entities.shopping.Auditable;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

import static jakarta.persistence.CascadeType.ALL;

/**
 * Transient shopping cart. Holds the items a (guest or logged-in) visitor
 * is still editing. Once checked out, an {@code Order} snapshot is created
 * and this basket is marked CHECKED_OUT (a fresh OPEN basket is created
 * on the next request).
 */
@Entity
@Table(name = "baskets")
public class Basket extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Getter
    private Long id;

    @Getter @Setter
    @Column(nullable = false, unique = true)
    private String token;

    @Getter @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Getter @Setter
    @OneToMany(mappedBy = "basket", cascade = ALL, orphanRemoval = true)
    private List<BasketItem> items = new ArrayList<>();

    @Getter @Setter
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BasketStatus status = BasketStatus.OPEN;

    public Basket() {
    }

    public boolean isOpen() {
        return status == BasketStatus.OPEN;
    }
}
