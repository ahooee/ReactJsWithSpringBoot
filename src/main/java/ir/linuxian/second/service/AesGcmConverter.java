package ir.linuxian.second.service;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Encrypts PII contact columns (user email/phone, verification contact) with
 * AES-256-GCM before they reach the database, so a DB dump never contains raw
 * contact data. Each value gets a fresh random IV; stored as
 * {@code v1:base64(iv || ciphertext)}.
 *
 * Key: SHA-256 of the secret resolved by {@link CryptoKeyStore} —
 * {@code APP_CRYPTO_KEY} env var first, else a persisted secret file
 * (generated on first boot), else an ephemeral key. See that class.
 *
 * Values without the {@code v1:} envelope (written before encryption existed)
 * pass through untouched on read, so enabling this never breaks old rows.
 *
 * Plain JPA converter (no Spring injection): Hibernate instantiates converters
 * itself, so the key comes straight from the environment / secret file.
 */
@Converter
public class AesGcmConverter implements AttributeConverter<String, String> {

    private static final Logger log = LoggerFactory.getLogger(AesGcmConverter.class);
    private static final String PREFIX = "v1:";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private static SecretKeySpec key() {
        return CryptoKeyStore.key();
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = cipher.doFinal(attribute.getBytes(StandardCharsets.UTF_8));
            byte[] joined = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, joined, 0, iv.length);
            System.arraycopy(ct, 0, joined, iv.length, ct.length);
            return PREFIX + Base64.getEncoder().encodeToString(joined);
        } catch (Exception e) {
            throw new IllegalStateException("Could not encrypt contact data", e);
        }
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null || !dbData.startsWith(PREFIX)) {
            return dbData;
        }
        try {
            byte[] joined = Base64.getDecoder().decode(dbData.substring(PREFIX.length()));
            byte[] iv = new byte[IV_BYTES];
            System.arraycopy(joined, 0, iv, 0, iv.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, iv));
            byte[] pt = cipher.doFinal(joined, iv.length, joined.length - iv.length);
            return new String(pt, StandardCharsets.UTF_8);
        } catch (Exception e) {
            // Availability first: a value encrypted under a lost key (e.g. the
            // server ran without APP_CRYPTO_KEY, whose key dies on restart) must
            // degrade to "no data", never 500 the request. The user re-saves it.
            log.error("Could not decrypt contact data (encrypted under a different "
                    + "APP_CRYPTO_KEY?). Returning null. Set a stable APP_CRYPTO_KEY "
                    + "and have the user re-enter the value.", e);
            return null;
        }
    }
}
