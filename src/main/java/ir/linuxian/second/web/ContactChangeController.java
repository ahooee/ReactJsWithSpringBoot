package ir.linuxian.second.web;

import ir.linuxian.second.service.DeliveryException;
import ir.linuxian.second.service.LoginAttemptService;
import ir.linuxian.second.service.RateLimitException;
import ir.linuxian.second.service.VerificationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Lets a logged-in user change the email/phone on their profile. The new
 * contact must be proven with a 6-digit code sent to it; the attempt is bound
 * to the authenticated username so codes cannot be replayed across accounts.
 */
@RestController
@RequestMapping("/api/auth/profile/contact")
public class ContactChangeController {

    private final VerificationService verificationService;
    private final LoginAttemptService rateLimit;

    public ContactChangeController(VerificationService verificationService,
                             LoginAttemptService rateLimit) {
        this.verificationService = verificationService;
        this.rateLimit = rateLimit;
    }

    private static String usernameOf(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        String name = authentication.getName();
        return (name == null || name.equals("anonymousUser")) ? null : name;
    }

    private static String clientIp(HttpServletRequest http) {
        String ip = http == null ? null : http.getRemoteAddr();
        return ip == null || ip.isBlank() ? "unknown" : ip;
    }

    private static String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    @PostMapping("/request")
    public ResponseEntity<?> request(@RequestBody Map<String, Object> body,
                                     Authentication authentication, HttpServletRequest http) {
        String username = usernameOf(authentication);
        if (username == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login required"));
        }
        String key = "contact-req:" + clientIp(http);
        try {
            rateLimit.check(key);
            Map<String, Object> out = verificationService.requestContactChange(
                    username,
                    str(body.get("channel")),
                    str(body.get("email")),
                    str(body.get("dial")),
                    str(body.get("phone")));
            rateLimit.success(key);
            return ResponseEntity.ok(out);
        } catch (RateLimitException e) {
            return ResponseEntity.status(429).body(Map.of("message", e.getMessage()));
        } catch (DeliveryException e) {
            rateLimit.failure(key);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            rateLimit.failure(key);
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/verify")
    public ResponseEntity<?> verify(@RequestBody Map<String, Object> body,
                                    Authentication authentication, HttpServletRequest http) {
        String username = usernameOf(authentication);
        if (username == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login required"));
        }
        String key = "contact-verify:" + clientIp(http);
        try {
            rateLimit.check(key);
            Map<String, Object> out = verificationService.verifyContactChange(
                    username, str(body.get("requestId")), str(body.get("code")));
            rateLimit.success(key);
            return ResponseEntity.ok(out);
        } catch (RateLimitException e) {
            return ResponseEntity.status(429).body(Map.of("message", e.getMessage()));
        } catch (IllegalStateException e) {
            rateLimit.failure(key);
            return ResponseEntity.status(HttpStatus.GONE).body(Map.of("message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            rateLimit.failure(key);
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
}
