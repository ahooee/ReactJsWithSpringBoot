package ir.linuxian.second.repos;

import ir.linuxian.second.entities.shopping.order.Order;
import ir.linuxian.second.entities.shopping.order.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface OrderItemRepo extends JpaRepository<OrderItem, Long> {
    List<OrderItem> findByOrder(Order order);

    List<OrderItem> findByOrderId(Long orderId);

    /** Books top sellers: purchase volume per library book, best first. */
    @Query("select p.bookId, sum(i.quantity) from OrderItem i join i.product p "
            + "where p.bookId is not null group by p.bookId order by sum(i.quantity) desc")
    List<Object[]> topSellingBookIds();
}
