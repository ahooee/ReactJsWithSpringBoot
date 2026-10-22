package ir.linuxian.second.web;

import ir.linuxian.second.service.BasketService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Basket endpoints only. Checkout moved to {@code OrderController}
 * (POST /api/orders/checkout) to keep basket and order logic separate.
 */
@RestController
@RequestMapping("/api/basket")
public class BasketController {

    private final BasketService basketService;

    public BasketController(BasketService basketService) {
        this.basketService = basketService;
    }

    @GetMapping
    public ResponseEntity<?> getBasket(
            @RequestHeader(value = "X-Basket-Token", required = false) String token,
            Authentication authentication) {
        try {
            return ResponseEntity.ok(basketService.getOrCreate(token, usernameOf(authentication)));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/items")
    public ResponseEntity<?> addItem(
            @RequestHeader(value = "X-Basket-Token", required = false) String token,
            Authentication authentication,
            @RequestBody Map<String, Object> body) {
        try {
            Long productId = toLong(body.get("productId"), "productId is required");
            int qty = toInt(body.get("qty"), 1);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(basketService.addItem(token, usernameOf(authentication), productId, qty));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PutMapping("/items/{itemId}")
    public ResponseEntity<?> setQty(
            @RequestHeader(value = "X-Basket-Token", required = false) String token,
            Authentication authentication,
            @PathVariable Long itemId,
            @RequestBody Map<String, Object> body) {
        try {
            int qty = toInt(body.get("qty"), 1);
            return ResponseEntity.ok(basketService.setQty(token, usernameOf(authentication), itemId, qty));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @DeleteMapping("/items/{itemId}")
    public ResponseEntity<?> removeItem(
            @RequestHeader(value = "X-Basket-Token", required = false) String token,
            Authentication authentication,
            @PathVariable Long itemId) {
        try {
            return ResponseEntity.ok(basketService.removeItem(token, usernameOf(authentication), itemId));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        }
    }

    @DeleteMapping
    public ResponseEntity<?> clear(
            @RequestHeader(value = "X-Basket-Token", required = false) String token,
            Authentication authentication) {
        try {
            return ResponseEntity.ok(basketService.clear(token, usernameOf(authentication)));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
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

    private Long toLong(Object value, String field) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        throw new RuntimeException(field + " is required");
    }

    private int toInt(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return fallback;
    }
}
