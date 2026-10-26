//part2:The database stores only a SHA-256 hash of each refresh token. The raw token is returned to the client and shown only once.
package com.example.jwt_security_api.service;
import com.example.jwt_security_api.entity.RefreshToken;
import com.example.jwt_security_api.entity.User;
import com.example.jwt_security_api.repository.RefreshTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

@Service 
public class RefreshTokenService {
    private final RefreshTokenRepository refreshTokenRepository;
    private final long ttlDays;
    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            @Value("${security.refresh-token.ttl-days}") long ttlDays) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.ttlDays = ttlDays;
    }

    @Transactional
    public String issue(User user) {
        String rawToken = createRawToken();

        refreshTokenRepository.save(
                new RefreshToken(
                        user,
                        hashToken(rawToken),
                        Instant.now().plusSeconds(ttlDays * 24 * 60 * 60)
                )
        );

        return rawToken;
    }
    @Transactional 
    public Rotation rotate(String rawToken) {
        RefreshToken storedToken = refreshTokenRepository
                .findActiveForUpdate(hashToken(rawToken))
                .orElseThrow(RefreshTokenService::invalidToken);

        if (!storedToken.getExpiresAt().isAfter(Instant.now())) {
            throw invalidToken();
        }

        storedToken.setRevoked(true);
        refreshTokenRepository.save(storedToken);

        String replacement = issue(storedToken.getUser());

        return new Rotation(storedToken.getUser(), replacement);
    }
    @Transactional 
    public void revoke(String rawToken) {
        refreshTokenRepository
                .findActiveForUpdate(hashToken(rawToken))
                .ifPresent(token -> {
                    token.setRevoked(true);
                    refreshTokenRepository.save(token);
                });
    }

    private String createRawToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static ResponseStatusException invalidToken() {
        return new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "Invalid or expired refresh token"
        );
    }

    public record Rotation(User user, String refreshToken) {
    }
}
