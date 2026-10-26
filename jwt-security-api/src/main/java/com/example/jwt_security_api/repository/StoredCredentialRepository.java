package com.example.jwt_security_api.repository;
import com.example.jwt_security_api.entity.StoredCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
public interface StoredCredentialRepository extends JpaRepository<StoredCredential, Long> {
    Optional<StoredCredential> findByUserUsernameAndProvider(
        String username, 
        String provider
    );

    @Query("select credential.provider from StoredCredential credential "
            + "where credential.user.username = :username order by credential.provider")
    List<String> findProviderNamesByUsername(@Param("username") String username);
}
