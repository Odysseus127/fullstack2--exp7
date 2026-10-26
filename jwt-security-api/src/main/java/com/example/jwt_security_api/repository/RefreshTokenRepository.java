//part 2: refresh tokens are stored in the database, but we don’t want to store them in plaintext. Instead, we store a hash of the token. When a refresh token is presented, we hash it and compare it to the stored hash. This way, if the database is compromised, the attacker cannot use the refresh tokens.
package com.example.jwt_security_api.repository;
import com.example.jwt_security_api.entity.RefreshToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select token
            from RefreshToken token
            where token.tokenHash = :tokenHash
              and token.revoked = false
            """)
    Optional<RefreshToken> findActiveForUpdate(@Param("tokenHash") String tokenHash);
    
}
