package de.hems.kasse.accounts;

import de.hems.kasse.config.KasseProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Symmetric encryption for stored account passwords.
 *
 * <p>Passwords are encrypted rather than hashed because the Passwort-Zettel has to be
 * reprintable at any time, which needs the plaintext back. That is a deliberate trade-off:
 * a database dump on its own reveals nothing, but whoever holds both the database and
 * {@code KASSE_SECRET_KEY} can read every password.
 *
 * <p>AES-256-GCM, a fresh 12-byte IV per encryption, stored as {@code base64(iv || ct||tag)}.
 * GCM authenticates the ciphertext, so a tampered value fails to decrypt instead of
 * returning garbage.
 */
@Component
public class SecretBox {

    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public SecretBox(KasseProperties props) {
        this.key = deriveKey(props.getSecretKey());
    }

    /**
     * The configured value is hashed to exactly 32 bytes so any sufficiently long passphrase
     * works, while still refusing something too short to be worth encrypting with.
     */
    private static SecretKey deriveKey(String configured) {
        String raw = configured == null ? "" : configured.trim();
        if (raw.length() < 32) {
            throw new IllegalStateException("""
                    KASSE_SECRET_KEY fehlt oder ist zu kurz (mindestens 32 Zeichen).
                    Damit werden die Konto-Passwörter verschlüsselt. Erzeugen mit:
                        openssl rand -base64 48
                    Achtung: Wird dieser Wert später geändert, lassen sich die gespeicherten
                    Passwörter nicht mehr entschlüsseln und müssen neu vergeben werden.""");
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(digest, "AES");
        } catch (Exception e) {
            throw new IllegalStateException("Could not derive the account encryption key", e);
        }
    }

    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("Could not encrypt the account password", e);
        }
    }

    /**
     * @throws SecretUnreadableException when the stored value cannot be decrypted — in practice
     *         that means KASSE_SECRET_KEY changed since the password was saved.
     */
    public String decrypt(String stored) {
        try {
            byte[] all = Base64.getDecoder().decode(stored);
            if (all.length <= IV_BYTES) throw new IllegalArgumentException("ciphertext too short");
            byte[] iv = new byte[IV_BYTES];
            System.arraycopy(all, 0, iv, 0, IV_BYTES);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] pt = cipher.doFinal(all, IV_BYTES, all.length - IV_BYTES);
            return new String(pt, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new SecretUnreadableException(e);
        }
    }

    /** Stored password cannot be read back with the current KASSE_SECRET_KEY. */
    public static class SecretUnreadableException extends RuntimeException {
        SecretUnreadableException(Throwable cause) {
            super("Gespeichertes Passwort lässt sich nicht entschlüsseln – wurde KASSE_SECRET_KEY geändert?", cause);
        }
    }
}
