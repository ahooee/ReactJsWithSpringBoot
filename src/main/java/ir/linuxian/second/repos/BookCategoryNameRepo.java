package ir.linuxian.second.repos;

import ir.linuxian.second.entities.book.BookCategoryName;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

@RepositoryRestResource(exported = false)
public interface BookCategoryNameRepo extends JpaRepository<BookCategoryName, String> {
}
