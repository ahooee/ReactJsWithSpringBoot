package ir.linuxian.second.web;

import ir.linuxian.second.service.Countries;
import ir.linuxian.second.service.DeliveryException;
import ir.linuxian.second.service.LoginAttemptService;
import ir.linuxian.second.service.RateLimitException;
import ir.linuxian.second.service.VerificationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth/register")
public class RegisterController {

    private final VerificationService verificationService;
    private final LoginAttemptService rateLimit;

    public RegisterController(VerificationService verificationService,
                              LoginAttemptService rateLimit) {
        this.verificationService = verificationService;
        this.rateLimit = rateLimit;
    }

    @GetMapping("/countries")
    public Map<String, Object> countries() {
        List<Map<String, String>> list = Countries.ALL.stream().map(Countries.Country::toMap).toList();
        return Map.of("defaultDial", verificationService.defaultDial(), "countries", list);
    }

    @PostMapping("/request")
    public ResponseEntity<?> request(@RequestBody Map<String, Object> body, HttpServletRequest http) {
        String key = "register:" + clientIp(http);
        try {
            rateLimit.check(key);
            Map<String, Object> out = verificationService.requestCode(
                    str(body.get("username")),
                    str(body.get("firstName")),
                    str(body.get("lastName")),
                    str(body.get("password")),
                    str(body.get("confirmPassword")),
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
        } catch (IllegalStateException e) {
            rateLimit.failure(key);
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            rateLimit.failure(key);
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/verify")
    public ResponseEntity<?> verify(@RequestBody Map<String, Object> body, HttpServletRequest http) {
        String key = "verify:" + clientIp(http);
        try {
            rateLimit.check(key);
            Map<String, Object> out = verificationService.verifyCode(
                    str(body.get("requestId")), str(body.get("code")));
            rateLimit.success(key);
            return ResponseEntity.ok()
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + out.get("token"))
                    .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, "Authorization")
                    .body(out);
        } catch (RateLimitException e) {
            return ResponseEntity.status(429).body(Map.of("message", e.getMessage()));
        } catch (IllegalStateException e) {
            rateLimit.failure(key);
            int status = e.getMessage() != null && e.getMessage().contains("already exists")
                    ? HttpStatus.CONFLICT.value() : HttpStatus.GONE.value();
            return ResponseEntity.status(status).body(Map.of("message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            rateLimit.failure(key);
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    private static String clientIp(HttpServletRequest http) {
        String ip = http == null ? null : http.getRemoteAddr();
        return ip == null || ip.isBlank() ? "unknown" : ip;
    }

    private static String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }
}
