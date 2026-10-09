package ir.linuxian.second.repos;

import ir.linuxian.second.entities.page.Page;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PageRepo extends JpaRepository<Page, Long> {

    Optional<Page> findBySlug(String slug);
}
