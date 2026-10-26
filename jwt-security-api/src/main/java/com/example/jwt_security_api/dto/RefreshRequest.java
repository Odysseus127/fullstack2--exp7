//part 2: refresh tokens are stored in the database, but we don’t want to store them in plaintext. Instead, we store a hash of the token. When a refresh token is presented, we hash it and compare it to the stored hash. This way, if the database is compromised, the attacker cannot use the refresh tokens.
package com.example.jwt_security_api.dto;
import jakarta.validation.constraints.NotBlank;
public record RefreshRequest( @NotBlank String refreshToken) {
}
