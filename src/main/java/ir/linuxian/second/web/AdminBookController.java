package ir.linuxian.second.web;

import ir.linuxian.second.dto.BookView;
import ir.linuxian.second.service.BookCatalogService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Admin management of the books library taxonomy (Admin → Categories):
 * dynamic category display-name translations and per-book category fixes.
 * Every route sits under {@code /api/admin/books/**} and therefore requires
 * ROLE_ADMIN (see SecurityConfig).
 */
@RestController
@RequestMapping("/api/admin/books")
public class AdminBookController {

    private final BookCatalogService service;

    public AdminBookController(BookCatalogService service) {
        this.service = service;
    }

    /**
     * Category chips as the admin sees them: groups derived live from the
     * books library (new categories appear automatically) joined with the
     * stored translations.
     */
    @GetMapping("/categories")
    public List<?> categories() {
        return service.findCategories();
    }

    /** Upsert the translation for a category slug ({@code nameFa}). A blank
     * value removes the translation so the UI falls back to the English
     * name. */
    @PutMapping("/categories/{slug}")
    public ResponseEntity<?> saveCategoryName(@PathVariable String slug,
            @RequestBody Map<String, String> body) {
        try {
            service.saveCategoryName(slug, body == null ? null : body.get("nameFa"));
            return ResponseEntity.ok(service.findCategories());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @DeleteMapping("/categories/{slug}")
    public ResponseEntity<?> deleteCategoryName(@PathVariable String slug) {
        service.deleteCategoryName(slug);
        return ResponseEntity.noContent().build();
    }

    /**
     * Books for the taxonomy fixer. Default (no params): the first page of
     * the catalog ordered by title. With {@code uncategorized=true}: only
     * books with a blank {@code categories}. With {@code query}: keyword
     * matches in title/author/categories.
     */
    @GetMapping
    public List<BookView> books(
            @RequestParam(required = false) String query,
            @RequestParam(required = false, defaultValue = "false") boolean uncategorized) {
        return service.findAdminBooks(query, uncategorized);
    }

    /** Rewrite one book's raw categories value ("Group - Leaf ...", blank
     * = uncategorized). Slugs/counts re-derive from the library. */
    @PutMapping("/{id}/category")
    public ResponseEntity<?> updateCategory(@PathVariable UUID id,
            @RequestBody Map<String, String> body) {
        try {
            return ResponseEntity.ok(service.updateBookCategories(id, body.get("categories")));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
}
