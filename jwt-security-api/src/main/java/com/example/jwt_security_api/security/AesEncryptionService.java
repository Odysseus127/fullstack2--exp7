//part 2 file: This encrypts recoverable secrets with AES-GCM. Continue to hash user passwords with BCrypt; don’t encrypt passwords.

package com.example.jwt_security_api.security;

import io.jsonwebtoken.io.Decoders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

@Service 
public class AesEncryptionService {
    private static final int NONCE_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom secureRandom = new SecureRandom();

    public AesEncryptionService(
            @Value("${security.encryption.aes-key-base64}") String keyBase64) {
        byte[] keyBytes = Decoders.BASE64.decode(keyBase64);

        if (keyBytes.length != 32) {
            throw new IllegalArgumentException(
                    "AES key must decode to exactly 32 bytes"
            );
        }
        this.key = new SecretKeySpec(keyBytes, "AES");
    }

    public String encrypt(String plaintext) {
        try {
            byte[] nonce = new byte[NONCE_LENGTH_BYTES];
            secureRandom.nextBytes(nonce);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    key,
                    new GCMParameterSpec(TAG_LENGTH_BITS, nonce)
            );

            byte[] ciphertext = cipher.doFinal(
                    plaintext.getBytes(StandardCharsets.UTF_8)
            );

            return "v1:"
                    + Base64.getEncoder().encodeToString(nonce)
                    + ":"
                    + Base64.getEncoder().encodeToString(ciphertext);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not encrypt sensitive data", e);
        }
    }

    public String decrypt(String encryptedValue) {
        String[] parts = encryptedValue.split(":", 3);

        if (parts.length != 3 || !"v1".equals(parts[0])) {
            throw new IllegalArgumentException("Unsupported encrypted value format");
        }

        try {
            byte[] nonce = Base64.getDecoder().decode(parts[1]);
            byte[] ciphertext = Base64.getDecoder().decode(parts[2]);

            if (nonce.length != NONCE_LENGTH_BYTES) {
                throw new IllegalArgumentException("Invalid AES-GCM nonce");
            }

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    key,
                    new GCMParameterSpec(TAG_LENGTH_BITS, nonce)
            );
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(
                    "Could not decrypt sensitive data; it may have been altered",
                    e
            );
        }
    }


}
