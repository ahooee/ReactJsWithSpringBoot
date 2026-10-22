package ir.linuxian.second.web;

import ir.linuxian.second.entities.shopping.order.Order;
import ir.linuxian.second.config.ZarinpalProperties;
import ir.linuxian.second.service.OrderService;
import ir.linuxian.second.service.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Map;

/**
 * Zarinpal callback (public, called by the gateway via the customer's browser).
 * On success/failure the browser is redirected to the frontend order-tracking page.
 */
@RestController
@RequestMapping("/api/payments")
public class PaymentController {
    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    private final PaymentService payments;
    private final OrderService orders;
    private final ZarinpalProperties props;

    public PaymentController(PaymentService payments, OrderService orders, ZarinpalProperties props) {
        this.payments = payments;
        this.orders = orders;
        this.props = props;
    }

    @GetMapping("/zarinpal/callback")
    public ResponseEntity<Void> callback(@RequestParam(value = "Authority", required = false) String authority,
                                         @RequestParam(value = "Status", required = false) String status) {
        if (authority == null || authority.isBlank()) {
            log.warn("Zarinpal callback without Authority (status={})", status);
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(frontendBase() + "/basket?payment=missing"))
                    .build();
        }
        Order order;
        try {
            order = payments.handleCallback(authority, status == null ? "" : status);
        } catch (Exception e) {
            log.error("Zarinpal callback failed for authority {}: {}", authority, e.getMessage());
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(frontendBase() + "/basket?payment=error"))
                    .build();
        }
        String token = order.getAccessToken();
        boolean paid = order.getStatus() != null
                && order.getStatus().name().equals("PAID");
        String target = frontendBase() + "/orders/track/" + token
                + (paid ? "?payment=success" : "?payment=failed");
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(target))
                .build();
    }

    /** JSON status for polling after redirect (guest allowed via secret token). */
    @GetMapping("/by-authority/{authority}")
    public ResponseEntity<?> byAuthority(@PathVariable String authority) {
        try {
            return ResponseEntity.ok(orders.getByAccessToken(
                    payments.findAccessTokenByAuthority(authority)));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        }
    }

    private String frontendBase() {
        String base = props.frontendBaseUrl();
        if (base == null || base.isBlank()) return "http://localhost:5173";
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        return base;
    }
}
