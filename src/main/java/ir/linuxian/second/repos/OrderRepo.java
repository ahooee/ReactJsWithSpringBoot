package ir.linuxian.second.repos;

import ir.linuxian.second.entities.User;
import ir.linuxian.second.entities.shopping.order.Order;
import ir.linuxian.second.entities.shopping.order.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrderRepo extends JpaRepository<Order, Long> {
    Optional<Order> findByAccessToken(String accessToken);

    Optional<Order> findByOrderNumber(String orderNumber);

    List<Order> findByUserOrderByOrderedAtDesc(User user);

    Optional<Order> findByIdAndUser(Long id, User user);

    Optional<Order> findByAccessTokenAndUser(String accessToken, User user);

    List<Order> findAllByOrderByOrderedAtDesc();

    List<Order> findByStatusOrderByOrderedAtDesc(OrderStatus status);
}
