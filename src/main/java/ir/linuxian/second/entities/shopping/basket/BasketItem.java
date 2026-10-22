package ir.linuxian.second.entities.shopping.basket;

import ir.linuxian.second.entities.product.Product;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "basket_items",
        uniqueConstraints = @UniqueConstraint(columnNames = {"basket_id", "product_id"}))
public class BasketItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Getter
    private Long id;

    @Getter @Setter
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "basket_id", nullable = false)
    private Basket basket;

    @Getter @Setter
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Getter @Setter
    @Column(nullable = false)
    private Integer qty = 1;

    public BasketItem() {
    }

    public BasketItem(Basket basket, Product product, int qty) {
        this.basket = basket;
        this.product = product;
        this.qty = qty;
    }
}
