package ir.linuxian.second.web;

import ir.linuxian.second.dto.BookCategoryView;
import ir.linuxian.second.dto.BookLanguageView;
import ir.linuxian.second.dto.BookReelsView;
import ir.linuxian.second.dto.BookView;
import ir.linuxian.second.entities.book.Book;
import ir.linuxian.second.service.BookCatalogService;
import ir.linuxian.second.service.BookPurchaseService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
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
    private final BookPurchaseService purchaseService;

    public BookController(BookCatalogService service, BookPurchaseService purchaseService) {
        this.service = service;
        this.purchaseService = purchaseService;
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

    /** Digikala-style horizontal reels: featured / recommended / top sellers. */
    @GetMapping("/reels")
    public BookReelsView reels() {
        return service.findReels();
    }

    /** Languages auto-discovered from the books table (with counts). */
    @GetMapping("/languages")
    public List<BookLanguageView> languages() {
        return service.findLanguages();
    }

    /** One downloaded book by id. */
    @GetMapping("/{id}")
    public ResponseEntity<BookView> one(@PathVariable UUID id) {
        return service.findDownloaded(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Add a catalog book to the basket. On first use the book gets its own
     * shop product (see BookPurchaseService) so checkout, payment and order
     * history work exactly as for ordinary products. Returns the full
     * basket response, mirroring the /api/basket endpoints.
     */
    @PostMapping("/{id}/purchase")
    public ResponseEntity<?> purchase(@PathVariable UUID id,
            @RequestHeader(value = "X-Basket-Token", required = false) String token,
            Authentication authentication,
            @RequestBody(required = false) Map<String, Object> body) {
        int qty = 1;
        if (body != null && body.get("qty") instanceof Number number) {
            qty = number.intValue();
        }
        try {
            return ResponseEntity.ok(purchaseService.purchase(token, usernameOf(authentication), id, qty));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    private static String usernameOf(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        String name = authentication.getName();
        return (name == null || name.equals("anonymousUser")) ? null : name;
    }

    /** Streams the cover stored in books.cover / books.cover_path. */
    @GetMapping("/{id}/cover")
    public ResponseEntity<?> cover(@PathVariable UUID id) {
        Optional<Book> found = service.findDownloadedBook(id);        if (found.isEmpty()) {
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
