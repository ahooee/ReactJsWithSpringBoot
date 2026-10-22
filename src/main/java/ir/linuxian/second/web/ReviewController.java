package ir.linuxian.second.web;

import ir.linuxian.second.entities.product.Review;
import ir.linuxian.second.service.ReviewService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping("/api/products/{id}/reviews")
    public List<Review> list(@PathVariable Long id) {
        return reviewService.list(id);
    }

    @PostMapping("/api/products/{id}/reviews")
    public ResponseEntity<?> add(@PathVariable Long id,
                                 @RequestBody Map<String, Object> body,
                                 Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login required"));
        }
        try {
            Object raw = body.get("rating");
            Integer rating = raw instanceof Number n ? n.intValue() : null;
            Object textRaw = body.get("text");
            String text = textRaw == null ? null : String.valueOf(textRaw);
            Review saved = reviewService.add(id, authentication.getName(), rating, text);
            return ResponseEntity.status(HttpStatus.CREATED).body(saved);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @DeleteMapping("/api/reviews/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        try {
            reviewService.delete(id);
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        }
    }
}
