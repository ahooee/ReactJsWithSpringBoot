package ir.linuxian.second.web;

import ir.linuxian.second.entities.page.Page;
import ir.linuxian.second.service.PageService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/pages")
public class PageController {

    private final PageService pageService;

    public PageController(PageService pageService) {
        this.pageService = pageService;
    }

    @GetMapping
    public List<Page> listAll() {
        return pageService.findAll();
    }

    @GetMapping("/{*slug}")
    public ResponseEntity<Map<String, Object>> getPageBySlug(
            @PathVariable String slug) {

        if (slug.startsWith("/")) {
            slug = slug.substring(1);
        }

        Map<String, Object> response = new LinkedHashMap<>();

        try {
            Page page = pageService.findBySlug(slug);

            response.put("success", true);
            response.put("message", "Page retrieved successfully");
            response.put("log", "Page found for slug: " + slug);
            response.put("data", page);

            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {
            response.put("success", false);
            response.put("message", "Page not found");
            response.put("log", "No page found for slug: " + slug);
            response.put("data", null);

            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(response);
        }
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Page page) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(pageService.create(page));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody Page page) {
        try {
            return ResponseEntity.ok(pageService.update(id, page));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        pageService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
