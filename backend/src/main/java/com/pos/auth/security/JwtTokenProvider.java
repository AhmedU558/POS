package com.pos.auth.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Component
public class JwtTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);

    private final JwtProperties jwtProperties;
    private final SecretKey key;
    private final SecretKey refreshKey;

    public JwtTokenProvider(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        this.key = Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
        this.refreshKey = Keys.hmacShaKeyFor(jwtProperties.getRefreshSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(String username) {
        Instant now = Instant.now();
        Instant expiryDate = now.plusMillis(jwtProperties.getExpirationMs());

        return Jwts.builder()
                .subject(username)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiryDate))
                .signWith(key)
                .compact();
    }

    public String generateRefreshToken(String username, String familyId, String jti) {
        Instant now = Instant.now();
        Instant expiryDate = now.plusMillis(jwtProperties.getRefreshExpirationMs());

        return Jwts.builder()
                .subject(username)
                .claim("family", familyId)
                .id(jti)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiryDate))
                .signWith(refreshKey)
                .compact();
    }

    public Claims getRefreshClaims(String token) {
        return Jwts.parser()
                
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String getUsernameFromJWT(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return claims.getSubject();
    }

    public Instant getIssuedAtFromJWT(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return claims.getIssuedAt().toInstant();
    }

    public boolean validateToken(String authToken) {
        try {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(authToken);
            return true;
        } catch (SignatureException ex) {
            log.error("Invalid JWT signature");
        } catch (MalformedJwtException ex) {
            log.error("Invalid JWT token");
        } catch (ExpiredJwtException ex) {
            log.error("Expired JWT token");
        } catch (UnsupportedJwtException ex) {
            log.error("Unsupported JWT token");
        } catch (IllegalArgumentException ex) {
            log.error("JWT claims string is empty.");
        }
        return false;
    }

    public boolean validateRefreshToken(String authToken) {
        try {
            Jwts.parser().build().parseSignedClaims(authToken);
            return true;
        } catch (SignatureException ex) {
            log.error("Invalid Refresh JWT signature");
        } catch (MalformedJwtException ex) {
            log.error("Invalid Refresh JWT token");
        } catch (ExpiredJwtException ex) {
            log.error("Expired Refresh JWT token");
        } catch (UnsupportedJwtException ex) {
            log.error("Unsupported Refresh JWT token");
        } catch (IllegalArgumentException ex) {
            log.error("Refresh JWT claims string is empty.");
        }
        return false;
    }
}
