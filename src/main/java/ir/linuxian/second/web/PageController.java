
package ir.linuxian.second.web;

import ir.linuxian.second.entities.page.Page;
import ir.linuxian.second.service.PageService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/pages")
public class PageController {

    private final PageService pageService;

    public PageController(PageService pageService) {
        this.pageService = pageService;
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
}