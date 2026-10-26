package com.example.jwt_security_api.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;/*part 2 import */
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
//import java.nio.charset.StandardCharsets;
import java.util.Date;
//import java.util.HashMap;
//import java.util.Map;

/*part 2 */
import java.time.Duration;
import java.time.Instant;


@Service
public class JwtService {
    private final SecretKey signingKey;
    private final Duration accessTokenTtl;

    //private final String SECRET_KEY =
    //        "mySuperSecretKeyForJwtAuthentication123456789";

    /*private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(
                SECRET_KEY.getBytes(StandardCharsets.UTF_8)
        );
    }*/
   //part 2
   public JwtService(
            @Value("${security.jwt.secret-base64}") String secretBase64,
            @Value("${security.jwt.access-token-ttl}") Duration accessTokenTtl) {
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secretBase64));
        this.accessTokenTtl = accessTokenTtl;
    }


    /*public String generateToken(UserDetails userDetails) {

        Map<String, Object> claims = new HashMap<>();

        return Jwts.builder()
                .claims(claims)
                .subject(userDetails.getUsername())
                .issuedAt(new Date())
                .expiration(
                        new Date(System.currentTimeMillis() + 1000 * 60 * 60)
                )
                .signWith(getSigningKey())
                .compact();
    }*/
   //part 2
   public String generateToken(UserDetails userDetails) {
        Instant now = Instant.now();

        return Jwts.builder()
                .subject(userDetails.getUsername())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTokenTtl)))
                .signWith(signingKey)
                .compact();
    }

    public String extractUsername(String token) {
        return extractAllClaims(token).getSubject();
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        Claims claims = extractAllClaims(token);

        return claims.getSubject().equals(userDetails.getUsername())
                && claims.getExpiration().after(new Date());
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)  //chnaged from getsigningkey to signingkey
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /*public boolean isTokenValid(
            String token,
            UserDetails userDetails) {

        String username = extractUsername(token);

        return username.equals(userDetails.getUsername());
    }*/
}
