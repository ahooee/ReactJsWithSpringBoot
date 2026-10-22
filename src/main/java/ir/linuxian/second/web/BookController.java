package ir.linuxian.second.web;

import ir.linuxian.second.dto.BookCategoryView;
import ir.linuxian.second.dto.BookView;
import ir.linuxian.second.entities.book.Book;
import ir.linuxian.second.service.BookCatalogService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Public read-only catalog for downloaded books. All data comes from the
 * {@code books} table; nothing here touches the products/categories tables.
 */
@RestController
@RequestMapping("/api/books")
public class BookController {

    private final BookCatalogService service;

    public BookController(BookCatalogService service) {
        this.service = service;
    }

    /** Every book with {@code downloaded = true}. */
    @GetMapping
    public List<BookView> list() {
        return service.findDownloaded();
    }

    /** Categories derived from the downloaded books' {@code categories} column. */
    @GetMapping("/categories")
    public List<BookCategoryView> categories() {
        return service.findCategories();
    }

    @GetMapping("/{id}")
    public ResponseEntity<BookView> one(@PathVariable UUID id) {
        return service.findDownloaded(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** Streams the cover stored in books.cover / books.cover_path. */
    @GetMapping("/{id}/cover")
    public ResponseEntity<?> cover(@PathVariable UUID id) {
        Optional<Book> found = service.findDownloadedBook(id);
        if (found.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Book book = found.get();

        String cover = service.preferredCover(book);
        if (cover != null && (cover.startsWith("http://") || cover.startsWith("https://"))) {
            return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(cover)).build();
        }

        Optional<Path> file = service.resolveCoverFile(book);
        if (file.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Path path = file.get();
        MediaType type = MediaTypeFactory.getMediaType(path.getFileName().toString())
                .orElse(MediaType.APPLICATION_OCTET_STREAM);
        Resource body = new FileSystemResource(path);
        return ResponseEntity.ok()
                .contentType(type)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePublic())
                .body(body);
    }
}
