package ir.linuxian.second.repos;

import ir.linuxian.second.entities.support.TicketMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TicketMessageRepo extends JpaRepository<TicketMessage, Long> {
    List<TicketMessage> findByTicketIdOrderByCreatedAtAsc(Long ticketId);
    long countByTicketId(Long ticketId);
    void deleteByTicketId(Long ticketId);
}
