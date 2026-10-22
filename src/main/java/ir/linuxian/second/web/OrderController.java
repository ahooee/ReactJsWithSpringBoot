package ir.linuxian.second.web;

import ir.linuxian.second.service.OrderService;
import ir.linuxian.second.service.PaymentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Order endpoints: checkout (basket -> order), user history,
 * guest tracking, and payment start. Admin lives in AdminOrderController.
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;
    private final PaymentService paymentService;

    public OrderController(OrderService orderService, PaymentService paymentService) {
        this.orderService = orderService;
        this.paymentService = paymentService;
    }

    private static String usernameOf(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        String name = authentication.getName();
        return (name == null || name.equals("anonymousUser")) ? null : name;
    }

    private static String str(Map<String, ?> body, String key) {
        if (body == null) return null;
        Object v = body.get(key);
        return v == null ? null : String.valueOf(v);
    }

    /** Checkout: POST /api/orders/checkout with X-Basket-Token header (guest allowed). */
    @PostMapping("/checkout")
    public ResponseEntity<?> checkout(
            @RequestHeader(value = "X-Basket-Token", required = false) String basketToken,
            Authentication authentication,
            @RequestBody(required = false) Map<String, String> body) {
        try {
            Map<String, Object> result = orderService.checkout(
                    basketToken,
                    usernameOf(authentication),
                    body == null ? null : body.get("name"),
                    body == null ? null : body.get("phone"),
                    body == null ? null : body.get("address"),
                    body == null ? null : body.get("email"));
            return ResponseEntity.status(HttpStatus.CREATED).body(result);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<?> list(Authentication authentication) {
        String username = usernameOf(authentication);
        if (username == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login required"));
        }
        return ResponseEntity.ok(orderService.listForUser(username));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> detail(@PathVariable Long id, Authentication authentication) {
        String username = usernameOf(authentication);
        if (username == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login required"));
        }
        try {
            return ResponseEntity.ok(orderService.getForUser(id, username));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        }
    }

    /** Guest tracking: GET /api/orders/track/{accessToken} (no login). */
    @GetMapping("/track/{accessToken}")
    public ResponseEntity<?> track(@PathVariable String accessToken) {
        try {
            return ResponseEntity.ok(orderService.getByAccessToken(accessToken));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        }
    }

    /** Start Zarinpal payment: POST /api/orders/track/{accessToken}/pay (guest allowed). */
    @PostMapping("/track/{accessToken}/pay")
    public ResponseEntity<?> payByToken(@PathVariable String accessToken,
                                        Authentication authentication) {
        try {
            return ResponseEntity.ok(paymentService.startPayment(accessToken, usernameOf(authentication)));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /** Start Zarinpal payment for a logged-in order id. */
    @PostMapping("/{id}/pay")
    public ResponseEntity<?> payById(@PathVariable Long id, Authentication authentication) {
        String username = usernameOf(authentication);
        if (username == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login required"));
        }
        try {
            Map<String, Object> detail = orderService.getForUser(id, username);
            String token = (String) detail.get("accessToken");
            return ResponseEntity.ok(paymentService.startPayment(token, username));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
}
