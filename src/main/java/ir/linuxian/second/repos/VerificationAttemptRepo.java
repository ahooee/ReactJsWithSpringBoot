package ir.linuxian.second.repos;

import ir.linuxian.second.entities.auth.VerificationAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VerificationAttemptRepo extends JpaRepository<VerificationAttempt, Long> {
    Optional<VerificationAttempt> findByRequestId(String requestId);

    List<VerificationAttempt> findByUsernameAndConsumedFalseOrderByCreatedAtDesc(String username);
}
