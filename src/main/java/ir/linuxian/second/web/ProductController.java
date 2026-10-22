package ir.linuxian.second.web;

import ir.linuxian.second.entities.product.Product;
import ir.linuxian.second.service.ProductService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public List<Product> list(@RequestParam(defaultValue = "false") boolean all) {
        if (all) {
            return productService.findAll();
        }
        return productService.findActive();
    }

    @GetMapping("/slug/{slug}")
    public ResponseEntity<?> getBySlug(
            @PathVariable String slug,
            @RequestHeader(value = "X-Viewer-Token", required = false) String viewerToken,
            Authentication authentication) {
        try {
            return ResponseEntity.ok(productService.findBySlugAndCountView(slug, usernameOf(authentication), viewerToken));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/{id}/reactions")
    public ResponseEntity<?> react(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        try {
            Object raw = body == null ? null : body.get("action");
            String action = raw == null ? null : String.valueOf(raw);
            return ResponseEntity.ok(productService.react(id, action));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Product product) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(productService.create(product));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody Product product) {
        try {
            return ResponseEntity.ok(productService.update(id, product));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        productService.delete(id);
        return ResponseEntity.noContent().build();
    }

    private String usernameOf(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        String name = authentication.getName();
        if (name == null || name.equals("anonymousUser")) {
            return null;
        }
        return name;
    }
}
