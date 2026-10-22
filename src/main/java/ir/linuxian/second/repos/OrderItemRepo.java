package ir.linuxian.second.repos;

import ir.linuxian.second.entities.shopping.order.Order;
import ir.linuxian.second.entities.shopping.order.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderItemRepo extends JpaRepository<OrderItem, Long> {
    List<OrderItem> findByOrder(Order order);

    List<OrderItem> findByOrderId(Long orderId);
}
