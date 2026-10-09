package ir.linuxian.second.web;

import ir.linuxian.second.entities.User;
import ir.linuxian.second.repos.UserRepo;
import ir.linuxian.second.service.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    public record SignupRequest(String username, String password) {
    }

    private final UserRepo userRepo;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    public AuthController(UserRepo userRepo,
                          PasswordEncoder passwordEncoder,
                          JwtService jwtService,
                          AuthenticationManager authenticationManager) {
        this.userRepo = userRepo;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody SignupRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        } catch (Exception e) {
            body.put("success", false);
            body.put("message", "Invalid username or password");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
        }

        User user = userRepo.findByUsername(request.username()).orElseThrow();

        body.put("success", true);
        body.put("token", jwtService.getToken(user.getUsername()));
        body.put("username", user.getUsername());
        body.put("role", user.getRole());
        return ResponseEntity.ok(body);
    }

    @PostMapping("/signup")
    public ResponseEntity<?> signup(@RequestBody SignupRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();

        if (request.username() == null || request.username().isBlank()
                || request.password() == null || request.password().length() < 4) {
            body.put("success", false);
            body.put("message", "Username is required and password must be at least 4 characters");
            return ResponseEntity.badRequest().body(body);
        }

        if (userRepo.findByUsername(request.username()).isPresent()) {
            body.put("success", false);
            body.put("message", "Username already taken");
            return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
        }

        User user = new User(
                request.username(),
                passwordEncoder.encode(request.password()),
                "user",
                new java.util.ArrayList<>()
        );
        userRepo.save(user);

        String token = jwtService.getToken(user.getUsername());

        body.put("success", true);
        body.put("message", "Account created");
        body.put("token", token);
        body.put("username", user.getUsername());
        body.put("role", user.getRole());
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        User user = userRepo.findByUsername(authentication.getName()).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", user.getUsername());
        body.put("role", user.getRole());
        body.put("roles", user.getRoles().stream().map(Object::toString).toList());
        return ResponseEntity.ok(body);
    }
}
