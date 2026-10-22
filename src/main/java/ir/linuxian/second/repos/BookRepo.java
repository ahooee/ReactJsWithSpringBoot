package ir.linuxian.second.repos;

import ir.linuxian.second.entities.book.Book;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import java.util.List;
import java.util.UUID;

@RepositoryRestResource(exported = false)
public interface BookRepo extends JpaRepository<Book, UUID> {

    /** The public catalog: every book whose file is present on disk. */
    List<Book> findByDownloadedTrueOrderByTitleAsc();

    /** Books still waiting to be uploaded to Telegram. */
    List<Book> findByDownloadedTrueAndUploadedFalseOrderByTitleAsc();

    long countByDownloadedTrue();

    long countByUploadedTrue();
}
