package com.pos.auth.service;

import com.pos.auth.security.JwtProperties;
import com.pos.auth.security.JwtTokenProvider;
import com.pos.common.exception.ApiException;
import com.pos.common.response.ErrorCode;
import io.jsonwebtoken.Claims;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class RefreshTokenService {

    private final StringRedisTemplate redisTemplate;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;

    private static final String FAMILY_PREFIX = "rt_family:";

    private static final String ROTATE_SCRIPT =
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
            "  redis.call('set', KEYS[1], ARGV[2], 'PX', ARGV[3]) " +
            "  return 1 " +
            "else " +
            "  return 0 " +
            "end";

    public RefreshTokenService(StringRedisTemplate redisTemplate, JwtTokenProvider jwtTokenProvider, JwtProperties jwtProperties) {
        this.redisTemplate = redisTemplate;
        this.jwtTokenProvider = jwtTokenProvider;
        this.jwtProperties = jwtProperties;
    }

    public String createRefreshToken(String username) {
        String familyId = UUID.randomUUID().toString();
        String jti = UUID.randomUUID().toString();
        
        redisTemplate.opsForValue().set(
                FAMILY_PREFIX + familyId, 
                jti, 
                jwtProperties.getRefreshExpirationMs(), 
                TimeUnit.MILLISECONDS
        );
        
        return jwtTokenProvider.generateRefreshToken(username, familyId, jti);
    }

    public RotationResult rotateRefreshToken(String token) {
        if (!jwtTokenProvider.validateRefreshToken(token)) {
            throw new ApiException(ErrorCode.AUTHENTICATION_REQUIRED, "Invalid refresh token");
        }

        Claims claims = jwtTokenProvider.getRefreshClaims(token);
        String username = claims.getSubject();
        String familyId = claims.get("family", String.class);
        String jti = claims.getId();

        String newJti = UUID.randomUUID().toString();
        
        Long result = redisTemplate.execute(
            new DefaultRedisScript<>(ROTATE_SCRIPT, Long.class),
            List.of(FAMILY_PREFIX + familyId),
            jti,
            newJti,
            String.valueOf(jwtProperties.getRefreshExpirationMs())
        );

        if (result == null || result == 0) {
            revokeFamily(familyId);
            throw new ApiException(ErrorCode.AUTHENTICATION_REQUIRED, "Invalid or compromised refresh token");
        }

        String newRefreshToken = jwtTokenProvider.generateRefreshToken(username, familyId, newJti);
        return new RotationResult(username, newRefreshToken, claims.getIssuedAt().toInstant());
    }

    public void revokeFamily(String familyId) {
        if (familyId != null) {
            redisTemplate.delete(FAMILY_PREFIX + familyId);
        }
    }

    public String getFamilyIdFromToken(String token) {
        try {
            Claims claims = jwtTokenProvider.getRefreshClaims(token);
            return claims.get("family", String.class);
        } catch (Exception e) {
            return null;
        }
    }

    public record RotationResult(String username, String newRefreshToken, java.time.Instant iat) {}
}