package com.pos.auth.security;

import com.pos.common.exception.ApiException;
import com.pos.common.response.ErrorCode;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class AuthenticationRateLimiter {

    private final StringRedisTemplate redisTemplate;

    private static final int MAX_ATTEMPTS = 5;
    private static final Duration LOCKOUT_DURATION = Duration.ofMinutes(15);

    public AuthenticationRateLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void checkAndRecordLoginAttempt(String ip, String username) {
        String ipKey = "rate_limit:login:ip:" + ip;
        String userKey = "rate_limit:login:user:" + username;

        checkLimit(ipKey);
        checkLimit(userKey);
    }

    public void recordFailedAttempt(String ip, String username) {
        incrementLimit("rate_limit:login:ip:" + ip);
        incrementLimit("rate_limit:login:user:" + username);
    }

    public void recordSuccessfulAttempt(String ip, String username) {
        redisTemplate.delete("rate_limit:login:ip:" + ip);
        redisTemplate.delete("rate_limit:login:user:" + username);
    }

    private void checkLimit(String key) {
        String value = redisTemplate.opsForValue().get(key);
        if (value != null && Integer.parseInt(value) >= MAX_ATTEMPTS) {
            throw new ApiException(ErrorCode.RATE_LIMITED, "Too many login attempts. Please try again later.");
        }
    }

    private void incrementLimit(String key) {
        Long attempts = redisTemplate.opsForValue().increment(key);
        if (attempts != null && attempts == 1) {
            redisTemplate.expire(key, LOCKOUT_DURATION);
        }
    }
}
