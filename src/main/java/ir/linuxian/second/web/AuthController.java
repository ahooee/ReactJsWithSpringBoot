package ir.linuxian.second.web;

import ir.linuxian.second.entities.AccountCredentials;
import ir.linuxian.second.entities.Role;
import ir.linuxian.second.entities.RoleName;
import ir.linuxian.second.entities.User;
import ir.linuxian.second.repos.RoleRepo;
import ir.linuxian.second.repos.UserRepo;
import ir.linuxian.second.service.JwtService;
import ir.linuxian.second.service.LoginAttemptService;
import ir.linuxian.second.service.RateLimitException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final UserRepo userRepo;
    private final RoleRepo roleRepo;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptService rateLimit;

    public AuthController(JwtService jwtService, AuthenticationManager authenticationManager,
                          UserRepo userRepo, RoleRepo roleRepo, PasswordEncoder passwordEncoder,
                          LoginAttemptService rateLimit) {
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
        this.userRepo = userRepo;
        this.roleRepo = roleRepo;
        this.passwordEncoder = passwordEncoder;
        this.rateLimit = rateLimit;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, Object> body, HttpServletRequest http) {
        String username = body.get("username") == null ? "" : String.valueOf(body.get("username")).trim();
        Object rawPassword = body.get("password");
        String password = rawPassword == null ? "" : String.valueOf(rawPassword);
        // Absent = true (previous clients stayed signed in indefinitely).
        boolean remember = !Boolean.FALSE.equals(body.get("remember"));
        String key = "login:" + clientIp(http) + ":" + username.toLowerCase();
        try {
            rateLimit.check(key);
        } catch (RateLimitException e) {
            return ResponseEntity.status(429).body(Map.of("message", e.getMessage()));
        }
        try {
            UsernamePasswordAuthenticationToken creds =
                    new UsernamePasswordAuthenticationToken(username, password);
            Authentication authentication = authenticationManager.authenticate(creds);
            rateLimit.success(key);
            long expiry = remember ? JwtService.REMEMBER_EXPIRATION_TIME : JwtService.EXPIRATION_TIME;
            String token = jwtService.getToken(authentication.getName(), expiry);
            User user = userRepo.findByUsername(authentication.getName()).orElse(null);
            return ResponseEntity.ok()
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, "Authorization")
                    .body(Map.of(
                            "token", token,
                            "username", authentication.getName(),
                            "role", user == null ? "" : String.valueOf(user.getRole()),
                            "remember", remember,
                            "expiresInSeconds", expiry / 1000
                    ));
        } catch (AuthenticationException e) {
            // Generic message on purpose: never reveal whether the username exists.
            rateLimit.failure(key);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Invalid username or password"));
        }
    }

    @PostMapping("/signup")
    public ResponseEntity<?> signup(@RequestBody AccountCredentials credentials, HttpServletRequest http) {
        String key = "signup:" + clientIp(http);
        try {
            rateLimit.check(key);
        } catch (RateLimitException e) {
            return ResponseEntity.status(429).body(Map.of("message", e.getMessage()));
        }
        if (credentials.username() == null || credentials.username().isBlank()
                || credentials.password() == null || credentials.password().length() < 8) {
            rateLimit.failure(key);
            return ResponseEntity.badRequest().body(Map.of("message", "Username is required and password must be at least 8 characters"));
        }
        if (userRepo.findByUsername(credentials.username()).isPresent()) {
            rateLimit.failure(key);
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", "Username already exists"));
        }
        Role userRole = null;
        for (Role r : roleRepo.findAll()) {
            if (r.getRoleName() == RoleName.ROLE_USER) {
                userRole = r;
                break;
            }
        }
        if (userRole == null) {
            userRole = roleRepo.save(new Role(RoleName.ROLE_USER));
        }

        User user = new User(
                credentials.username(),
                passwordEncoder.encode(credentials.password()),
                "user",
                List.of(userRole));
        userRepo.save(user);

        String token = jwtService.getToken(user.getUsername(), JwtService.REMEMBER_EXPIRATION_TIME);
        rateLimit.success(key);
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(Map.of("token", token, "username", user.getUsername(), "role", "user",
                        "remember", true, "expiresInSeconds", JwtService.REMEMBER_EXPIRATION_TIME / 1000));
    }

    private static String clientIp(HttpServletRequest http) {
        String ip = http == null ? null : http.getRemoteAddr();
        return ip == null || ip.isBlank() ? "unknown" : ip;
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Not authenticated"));
        }
        String username = authentication.getName();
        User user = userRepo.findByUsername(username).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "User not found"));
        }
        return ResponseEntity.ok(Map.of(
                "username", user.getUsername(),
                "role", String.valueOf(user.getRole()),
                "roles", user.getRoles().stream().map(Role::toString).toList(),
                "firstName", user.getFirstName() == null ? "" : user.getFirstName(),
                "lastName", user.getLastName() == null ? "" : user.getLastName(),
                "email", user.getEmail() == null ? "" : user.getEmail(),
                "phone", user.getPhone() == null ? "" : user.getPhone()
        ));
    }

    @PutMapping("/me")
    public ResponseEntity<?> updateMe(@RequestBody Map<String, Object> body, Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Not authenticated"));
        }
        User user = userRepo.findByUsername(authentication.getName()).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "User not found"));
        }
        String firstName = str(body.get("firstName")).trim();
        String lastName = str(body.get("lastName")).trim();
        // NOTE: email/phone are intentionally NOT updated here. Changing contact
        // info requires a verification code (see ProfileController).
        user.setFirstName(firstName);
        user.setLastName(lastName);
        userRepo.save(user);
        return ResponseEntity.ok(Map.of(
                "username", user.getUsername(),
                "role", String.valueOf(user.getRole()),
                "roles", user.getRoles().stream().map(Role::toString).toList(),
                "firstName", user.getFirstName() == null ? "" : user.getFirstName(),
                "lastName", user.getLastName() == null ? "" : user.getLastName(),
                "email", user.getEmail() == null ? "" : user.getEmail(),
                "phone", user.getPhone() == null ? "" : user.getPhone()
        ));
    }

    private static String str(Object v) {
        return v == null ? "" : String.valueOf(v);
    }
}
