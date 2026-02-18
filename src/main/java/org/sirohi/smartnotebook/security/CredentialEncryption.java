package org.sirohi.smartnotebook.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM encryption utility for API credentials.
 *
 * Security features:
 * - AES-256-GCM authenticated encryption
 * - Unique IV per encryption (12 bytes)
 * - Authentication tag prevents tampering (128 bits)
 *
 * Interview Note: GCM mode provides both confidentiality and integrity,
 * preventing both data exposure and tampering without needing a separate HMAC.
 */
@Component
public class CredentialEncryption {

    private static final Logger log = LoggerFactory.getLogger(CredentialEncryption.class);

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;      // 96 bits recommended for GCM
    private static final int GCM_TAG_LENGTH = 128;    // 128-bit auth tag

    private final SecretKey secretKey;
    private final boolean encryptionEnabled;

    public CredentialEncryption(
            @Value("${app.security.encryption-key:}") String base64Key) {

        if (base64Key == null || base64Key.isBlank()) {
            log.warn("No encryption key configured. Credentials will be stored as plaintext. "
                    + "Set MODEL_ENCRYPTION_KEY environment variable for production.");
            this.secretKey = null;
            this.encryptionEnabled = false;
        } else {
            try {
                byte[] keyBytes = Base64.getDecoder().decode(base64Key);
                if (keyBytes.length != 32) {
                    throw new IllegalArgumentException(
                        "Encryption key must be 32 bytes (256 bits). Got " + keyBytes.length);
                }
                this.secretKey = new SecretKeySpec(keyBytes, "AES");
                this.encryptionEnabled = true;
                log.info("Credential encryption initialized with AES-256-GCM");
            } catch (Exception e) {
                throw new IllegalStateException("Failed to initialize encryption", e);
            }
        }
    }

    /**
     * Encrypt plaintext using AES-256-GCM.
     * Output format: Base64(IV || Ciphertext || AuthTag)
     *
     * @param plaintext The text to encrypt
     * @return Base64-encoded ciphertext, or plaintext if encryption disabled
     */
    public String encrypt(String plaintext) {
        if (!encryptionEnabled || plaintext == null) {
            return plaintext;
        }

        try {
            // Generate random IV
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            // Initialize cipher
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec);

            // Encrypt
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            // Combine IV + ciphertext (auth tag is appended by GCM)
            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);

            return Base64.getEncoder().encodeToString(combined);

        } catch (Exception e) {
            log.error("Encryption failed", e);
            throw new SecurityException("Failed to encrypt credential", e);
        }
    }

    /**
     * Decrypt ciphertext using AES-256-GCM.
     *
     * @param ciphertext Base64-encoded encrypted text
     * @return Decrypted plaintext, or input if encryption disabled
     */
    public String decrypt(String ciphertext) {
        if (!encryptionEnabled || ciphertext == null) {
            return ciphertext;
        }

        try {
            byte[] combined = Base64.getDecoder().decode(ciphertext);

            // Extract IV
            byte[] iv = new byte[GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);

            // Extract ciphertext + auth tag
            byte[] encrypted = new byte[combined.length - GCM_IV_LENGTH];
            System.arraycopy(combined, GCM_IV_LENGTH, encrypted, 0, encrypted.length);

            // Initialize cipher
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);

            // Decrypt
            byte[] plaintext = cipher.doFinal(encrypted);
            return new String(plaintext, StandardCharsets.UTF_8);

        } catch (Exception e) {
            log.error("Decryption failed", e);
            throw new SecurityException("Failed to decrypt credential", e);
        }
    }

    /**
     * Check if encryption is enabled.
     */
    public boolean isEncryptionEnabled() {
        return encryptionEnabled;
    }

    /**
     * Mask API key for display: "sk-abc123xyz" → "sk-...xyz"
     * Shows first 3 and last 4 characters.
     */
    public static String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.length() < 8) {
            return "***";
        }
        String prefix = apiKey.substring(0, Math.min(3, apiKey.length()));
        String suffix = apiKey.substring(Math.max(0, apiKey.length() - 4));
        return prefix + "..." + suffix;
    }

    /**
     * Generate a new encryption key (for initial setup).
     * Run: java -cp ... CredentialEncryption.main
     */
    public static void main(String[] args) {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        String base64Key = Base64.getEncoder().encodeToString(key);
        System.out.println("Generated encryption key (add to environment):");
        System.out.println("MODEL_ENCRYPTION_KEY=" + base64Key);
    }
}
