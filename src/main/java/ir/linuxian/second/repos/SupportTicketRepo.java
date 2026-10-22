package ir.linuxian.second.repos;

import ir.linuxian.second.entities.support.SupportTicket;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SupportTicketRepo extends JpaRepository<SupportTicket, Long> {
    List<SupportTicket> findByUsernameOrderByUpdatedAtDesc(String username);
    List<SupportTicket> findAllByOrderByUpdatedAtDesc();
    List<SupportTicket> findByStatusOrderByUpdatedAtDesc(String status);
    long countByStatus(String status);
}
