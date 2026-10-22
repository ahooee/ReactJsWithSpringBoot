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

import java.util.Locale;
import java.util.Map;

/**
 * Password lifecycle endpoints.
 *
 * <p>{@code /change} is for a signed-in user and requires the current
 * password. The two {@code /forgot} endpoints are public and use the same
 * one-time-code verification service as sign-up/contact changes.
 */
@RestController
@RequestMapping("/api/auth/password")
public class PasswordController {

    private final VerificationService verificationService;
    private final LoginAttemptService rateLimit;

    public PasswordController(VerificationService verificationService,
                              LoginAttemptService rateLimit) {
        this.verificationService = verificationService;
        this.rateLimit = rateLimit;
    }

    @PostMapping("/change")
    public ResponseEntity<?> change(@RequestBody Map<String, Object> body,
                                    Authentication authentication,
                                    HttpServletRequest http) {
        String username = usernameOf(authentication);
        if (username == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Login required"));
        }
        String key = "password-change:" + clientIp(http) + ":"
                + username.toLowerCase(Locale.ROOT);
        try {
            rateLimit.check(key);
            verificationService.changePassword(
                    username,
                    str(body.get("currentPassword")),
                    str(body.get("newPassword")),
                    str(body.get("confirmPassword")));
            rateLimit.success(key);
            return ResponseEntity.ok(Map.of("message", "Password changed."));
        } catch (RateLimitException e) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("message", e.getMessage()));
        } catch (IllegalStateException e) {
            rateLimit.failure(key);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            rateLimit.failure(key);
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/forgot/request")
    public ResponseEntity<?> forgotRequest(@RequestBody Map<String, Object> body,
                                           HttpServletRequest http) {
        // Count every request towards the quota: each one can send an email
        // or Telegram message, so unlimited retries would be abusable.
        String key = "password-request:" + clientIp(http);
        try {
            rateLimit.check(key);
            Map<String, Object> out = verificationService.requestPasswordReset(
                    str(body.get("channel")),
                    str(body.get("email")),
                    str(body.get("dial")),
                    str(body.get("phone")));
            rateLimit.failure(key);
            return ResponseEntity.ok(out);
        } catch (RateLimitException e) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("message", e.getMessage()));
        } catch (DeliveryException e) {
            rateLimit.failure(key);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            rateLimit.failure(key);
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/forgot/reset")
    public ResponseEntity<?> forgotReset(@RequestBody Map<String, Object> body,
                                         HttpServletRequest http) {
        String key = "password-reset:" + clientIp(http);
        try {
            rateLimit.check(key);
            verificationService.resetPassword(
                    str(body.get("requestId")),
                    str(body.get("code")),
                    str(body.get("newPassword")),
                    str(body.get("confirmPassword")));
            rateLimit.success(key);
            return ResponseEntity.ok(Map.of("message", "Password changed."));
        } catch (RateLimitException e) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("message", e.getMessage()));
        } catch (IllegalStateException e) {
            rateLimit.failure(key);
            return ResponseEntity.status(HttpStatus.GONE)
                    .body(Map.of("message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            rateLimit.failure(key);
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

    private static String clientIp(HttpServletRequest http) {
        String ip = http == null ? null : http.getRemoteAddr();
        return ip == null || ip.isBlank() ? "unknown" : ip;
    }

    private static String str(Object v) {
        return v == null ? "" : String.valueOf(v);
    }
}
