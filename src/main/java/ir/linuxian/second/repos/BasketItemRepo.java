package ir.linuxian.second.repos;

import ir.linuxian.second.entities.shopping.basket.Basket;
import ir.linuxian.second.entities.shopping.basket.BasketItem;
import ir.linuxian.second.entities.product.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface BasketItemRepo extends JpaRepository<BasketItem, Long> {
    List<BasketItem> findByBasket(Basket basket);

    Optional<BasketItem> findByBasketAndProduct(Basket basket, Product product);

    void deleteByBasket(Basket basket);

    @Modifying
    @Transactional
    @Query("delete from BasketItem i where i.product.id = :productId")
    int deleteByProductId(@Param("productId") Long productId);
}
