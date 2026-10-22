package ir.linuxian.second.repos;

import ir.linuxian.second.entities.shopping.basket.Basket;
import ir.linuxian.second.entities.shopping.basket.BasketStatus;
import ir.linuxian.second.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BasketRepo extends JpaRepository<Basket, Long> {
    Optional<Basket> findByToken(String token);

    List<Basket> findByUserAndStatus(User user, BasketStatus status);
}
