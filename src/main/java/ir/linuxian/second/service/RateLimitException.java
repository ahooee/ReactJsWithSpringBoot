package ir.linuxian.second.service;

/** Thrown when an auth endpoint is hit too many times in a short window. Mapped to HTTP 429. */
public class RateLimitException extends RuntimeException {
    public RateLimitException(String message) {
        super(message);
    }
}
