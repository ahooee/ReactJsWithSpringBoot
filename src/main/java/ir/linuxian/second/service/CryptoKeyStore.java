package ir.linuxian.second.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.EnumSet;
import java.util.Set;

/**
 * Home of the contact-encryption secret ({@code APP_CRYPTO_KEY}).
 *
 * <p>Resolution order, cached process-wide after first use:
 * <ol>
 *   <li>{@code APP_CRYPTO_KEY} environment variable (production: set it once, never change it),</li>
 *   <li>a persisted secret file (default {@code ./.app-crypto-key},
 *       override with {@code APP_CRYPTO_KEY_FILE} env var), generated on
 *       first boot when neither exists,</li>
 *   <li>an ephemeral in-memory key (last resort: values stay encrypted but
 *       become unreadable after a restart).</li>
 * </ol>
 *
 * <p>Plain static helper on purpose: JPA attribute converters are
 * instantiated by Hibernate, outside Spring, so they cannot use injection.
 */
public final class CryptoKeyStore {

    private static final Logger log = LoggerFactory.getLogger(CryptoKeyStore.class);

    static final String ENV_KEY = "APP_CRYPTO_KEY";
    static final String ENV_FILE = "APP_CRYPTO_KEY_FILE";
    static final String DEFAULT_FILE = ".app-crypto-key";
    private static final int SECRET_BYTES = 32;

    private static volatile SecretKeySpec cached;

    private CryptoKeyStore() {
    }

    /** Process-wide encryption key, resolved once. Never logs the secret itself. */
    public static SecretKeySpec key() {
        SecretKeySpec hit = cached;
        if (hit != null) {
            return hit;
        }
        synchronized (CryptoKeyStore.class) {
            if (cached == null) {
                cached = resolve();
            }
            return cached;
        }
    }

    /**
     * Make sure a usable secret exists before any encrypted data is touched.
     * Creates the secret file on first boot. Safe to call repeatedly and
     * concurrently; returns the key file in use, or null when the environment
     * key is used (no file needed) — and also null when only an ephemeral
     * key could be arranged (logged loudly by {@link #key()}).
     */
    public static Path ensureKeyFile() {
        if (hasEnvKey()) {
            log.info("Contact encryption: using {} from the environment.", ENV_KEY);
            return null;
        }
        Path file = keyFile();
        try {
            if (isBlankFile(file)) {
                writeNewSecret(file);
                log.warn("Contact encryption: no {} set — generated a new secret and stored it in {}. "
                        + "Back this file up alongside database backups; losing it makes stored "
                        + "emails/phone numbers unreadable.", ENV_KEY, file);
            } else {
                log.info("Contact encryption: using persisted secret file {}.", file);
            }
            return file;
        } catch (Exception e) {
            log.error("Contact encryption: cannot use secret file {} ({}). "
                    + "Set {} or fix file permissions.", file, e.getMessage(), ENV_KEY);
            return null;
        }
    }

    /** Where the file secret lives (absolute, normalized). */
    public static Path keyFile() {
        String configured = System.getenv(ENV_FILE);
        if (configured == null || configured.isBlank()) {
            configured = System.getProperty("app.crypto-key-file", DEFAULT_FILE);
        }
        return Paths.get(configured.trim()).toAbsolutePath().normalize();
    }

    private static SecretKeySpec resolve() {
        String configured = System.getenv(ENV_KEY);
        if (configured != null && !configured.trim().isEmpty()) {
            return toKey(configured.trim().getBytes(StandardCharsets.UTF_8), ENV_KEY);
        }
        Path file = keyFile();
        try {
            String stored = readSecret(file);
            if (stored != null) {
                return toKey(stored.getBytes(StandardCharsets.UTF_8), file.toString());
            }
            writeNewSecret(file);
            stored = readSecret(file);
            if (stored != null) {
                log.warn("Contact encryption: no {} set — generated a new secret and stored it in {}. "
                        + "Back this file up alongside database backups.", ENV_KEY, file);
                return toKey(stored.getBytes(StandardCharsets.UTF_8), file.toString());
            }
        } catch (Exception e) {
            log.error("Contact encryption: cannot use secret file {} ({}).",
                    file, e.getMessage());
        }
        byte[] fresh = new byte[SECRET_BYTES];
        new SecureRandom().nextBytes(fresh);
        log.error("Contact encryption: NO usable secret (no {} and no writable secret file). "
                        + "Using an ephemeral key: contact data will be UNREADABLE after a restart. "
                        + "Set {} or make {} writable.",
                ENV_KEY, ENV_KEY, file);
        return new SecretKeySpec(fresh, "AES");
    }

    private static SecretKeySpec toKey(byte[] raw, String source) {
        try {
            // Accept any passphrase: SHA-256 compresses it to a 256-bit key.
            return new SecretKeySpec(MessageDigest.getInstance("SHA-256").digest(raw), "AES");
        } catch (Exception e) {
            throw new IllegalStateException("Could not initialize contact encryption from " + source, e);
        }
    }

    private static boolean hasEnvKey() {
        String configured = System.getenv(ENV_KEY);
        return configured != null && !configured.trim().isEmpty();
    }

    private static boolean isBlankFile(Path file) throws IOException {
        if (!Files.isRegularFile(file)) {
            return true;
        }
        return readSecret(file) == null;
    }

    private static String readSecret(Path file) throws IOException {
        if (!Files.isRegularFile(file)) {
            return null;
        }
        String stored = new String(Files.readAllBytes(file), StandardCharsets.UTF_8).trim();
        return stored.isEmpty() ? null : stored;
    }

    private static void writeNewSecret(Path file) throws IOException {
        byte[] raw = new byte[SECRET_BYTES];
        new SecureRandom().nextBytes(raw);
        String secret = Base64.getEncoder().encodeToString(raw);
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try {
            Files.write(file, (secret + "\n").getBytes(StandardCharsets.UTF_8),
                    java.nio.file.StandardOpenOption.CREATE_NEW,
                    java.nio.file.StandardOpenOption.WRITE);
        } catch (FileAlreadyExistsException alreadyThere) {
            if (isBlankFile(file)) {
                // Pre-existing but empty file: claim it with a fresh secret.
                Files.write(file, (secret + "\n").getBytes(StandardCharsets.UTF_8),
                        java.nio.file.StandardOpenOption.TRUNCATE_EXISTING,
                        java.nio.file.StandardOpenOption.WRITE);
            } else {
                // A concurrent boot won the race: use its secret instead.
                log.info("Contact encryption: secret file {} appeared concurrently; using it.", file);
                return;
            }
        }
        restrictPermissions(file);
    }

    private static void restrictPermissions(Path file) {
        try {
            Set<PosixFilePermission> perms =
                    EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
            Files.setPosixFilePermissions(file, perms);
        } catch (UnsupportedOperationException notPosix) {
            try {
                file.toFile().setReadable(false, false);
                file.toFile().setWritable(false, false);
                file.toFile().setReadable(true, true);
                file.toFile().setWritable(true, true);
            } catch (Exception ignored) {
                // best effort on non-POSIX filesystems
            }
        } catch (Exception e) {
            log.warn("Contact encryption: could not restrict permissions on {}: {}",
                    file, e.getMessage());
        }
    }
}
