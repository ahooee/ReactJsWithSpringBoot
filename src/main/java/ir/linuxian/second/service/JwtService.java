package ir.linuxian.second.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Date;


@Component
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    public static final long EXPIRATION_TIME = 1000 * 60 * 60 * 12;
    /** Remember-me sessions (login checkbox, new registrations): 5 days. */
    public static final long REMEMBER_EXPIRATION_TIME = 1000L * 60 * 60 * 24 * 5;
    static final String TOKEN_PREFIX = "Bearer ";

    static final SecretKey key = initKey();

    private static SecretKey initKey() {
        try {
            String configured = System.getenv("APP_JWT_SECRET");
            byte[] raw = configured == null ? new byte[0]
                    : configured.trim().getBytes(StandardCharsets.UTF_8);
            if (raw.length >= 32) {
                // SHA-256 compresses any long passphrase to a 256-bit HS256 key.
                return Keys.hmacShaKeyFor(MessageDigest.getInstance("SHA-256").digest(raw));
            }
            log.warn("APP_JWT_SECRET is not set (or shorter than 32 characters): using an "
                    + "ephemeral signing key. All tokens invalidate on restart. "
                    + "Set APP_JWT_SECRET to a long random secret in production.");
            return Keys.secretKeyFor(SignatureAlgorithm.HS256);
        } catch (Exception e) {
            throw new IllegalStateException("Could not initialize JWT signing key", e);
        }
    }

    public String getToken(String username){

        String token = Jwts.builder().setSubject(username).setExpiration(new Date(System.currentTimeMillis()+ EXPIRATION_TIME)).signWith(key).compact();

        return token;

    }

    public String getToken(String username, long expirationMillis){

        String token = Jwts.builder().setSubject(username).setExpiration(new Date(System.currentTimeMillis()+ expirationMillis)).signWith(key).compact();

        return token;

    }

    public String getAuthUser(HttpServletRequest request) {
        String token = request.getHeader
                (HttpHeaders.AUTHORIZATION);
        if (token != null) {
            String user = Jwts.parser()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token.replace(TOKEN_PREFIX, ""))
                    .getBody()
                    .getSubject();
            if (user != null)
                return user;
        }
        return null;
    }

}
