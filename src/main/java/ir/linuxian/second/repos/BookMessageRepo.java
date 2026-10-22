package ir.linuxian.second.repos;

import ir.linuxian.second.entities.book.BookMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

@RepositoryRestResource(exported = false)
public interface BookMessageRepo extends JpaRepository<BookMessage, Long> {
}
