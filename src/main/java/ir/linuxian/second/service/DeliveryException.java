package ir.linuxian.second.service;

/** Thrown when a verification code cannot be delivered. Mapped to HTTP 503. */
public class DeliveryException extends RuntimeException {
    public DeliveryException(String message) {
        super(message);
    }
}
