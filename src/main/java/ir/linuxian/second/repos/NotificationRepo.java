package ir.linuxian.second.repos;

import ir.linuxian.second.entities.notification.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationRepo extends JpaRepository<Notification, Long> {
    List<Notification> findByUsernameOrderByCreatedAtDesc(String username);
    long countByUsernameAndReadFalse(String username);
    Optional<Notification> findByIdAndUsername(Long id, String username);
    void deleteByUsername(String username);
}
