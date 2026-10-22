package ir.linuxian.second.entities.auth;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "verification_attempts")
public class VerificationAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Getter
    private Long id;

    @Getter @Setter
    @Column(nullable = false, unique = true)
    private String requestId;

    /** EMAIL or PHONE */
    @Getter @Setter
    @Column(nullable = false)
    private String channel;

    /** Lower-cased email address or full international phone (e.g. +98912...). Stored encrypted. */
    @Getter @Setter
    @Convert(converter = ir.linuxian.second.service.AesGcmConverter.class)
    @Column(nullable = false, columnDefinition = "TEXT")
    private String contact;

    @Getter @Setter
    @Column(nullable = false)
    private String username;

    @Getter @Setter
    private String firstName = "";

    @Getter @Setter
    private String lastName = "";

    @Getter @Setter
    @Column(nullable = false)
    private String passwordHash;

    /** REGISTER, CONTACT_CHANGE or PASSWORD_RESET */
    @Getter @Setter
    private String purpose = "REGISTER";

    @Getter @Setter
    @Column(nullable = false, columnDefinition = "TEXT")
    private String code;

    @Getter @Setter
    @Column(nullable = false)
    private Instant expiresAt;

    @Getter @Setter
    @Column(nullable = false)
    private int attempts = 0;

    @Getter @Setter
    @Column(nullable = false)
    private boolean consumed = false;

    @Getter
    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public VerificationAttempt() {
    }
}
