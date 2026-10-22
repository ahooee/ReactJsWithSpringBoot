package ir.linuxian.second.repos;

import ir.linuxian.second.entities.payment.Payment;
import ir.linuxian.second.entities.shopping.order.Order;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PaymentRepo extends JpaRepository<Payment, Long> {
    Optional<Payment> findByAuthority(String authority);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.authority = :authority")
    Optional<Payment> findByAuthorityForUpdate(@Param("authority") String authority);

    List<Payment> findByOrder(Order order);

    List<Payment> findByOrderId(Long orderId);
}
