package ir.linuxian.second.repos;

import ir.linuxian.second.entities.book.Book;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import java.util.List;
import java.util.UUID;

@RepositoryRestResource(exported = false)
public interface BookRepo extends JpaRepository<Book, UUID> {

    /** The public catalog: every book whose file is present on disk. */
    List<Book> findByDownloadedTrueOrderByTitleAsc();

    List<Book> findByDownloadedTrueOrderByTitleAsc(Pageable pageable);

    /** Books still waiting to be uploaded to Telegram. */
    List<Book> findByDownloadedTrueAndUploadedFalseOrderByTitleAsc();

    long countByDownloadedTrue();

    long countByUploadedTrue();

    /**
     * Admin taxonomy fixer: search downloaded books by a keyword across
     * title, author and the raw categories value.
     */
    @Query("""
            select b from Book b
            where b.downloaded = true
              and (lower(b.title) like lower(concat('%', :q, '%'))
                   or lower(b.author) like lower(concat('%', :q, '%'))
                   or lower(b.categories) like lower(concat('%', :q, '%')))
            order by b.title asc
            """)
    List<Book> searchDownloaded(@Param("q") String q, Pageable pageable);

    /** Admin taxonomy fixer: downloaded books that have no category. */
    @Query("""
            select b from Book b
            where b.downloaded = true
              and (b.categories is null or trim(b.categories) = '')
            order by b.title asc
            """)
    List<Book> findDownloadedUncategorized(Pageable pageable);
}
